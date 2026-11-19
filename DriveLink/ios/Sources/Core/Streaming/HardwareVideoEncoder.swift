import CoreMedia
import CoreVideo
import Foundation
import VideoToolbox

/// Zero-latency hardware H.264 / HEVC video encoder using Apple VideoToolbox.
/// Outputs Annex-B (`00 00 00 01`) NALUs with inline VPS/SPS/PPS parameter sets on keyframes
/// so BYD DiLink Android MediaCodec decodes streams without extra out-of-band conversion.
public final class HardwareVideoEncoder: @unchecked Sendable {

    private var compressionSession: VTCompressionSession?
    private let isHevc: Bool
    private var activeCodecIsHevc: Bool = false
    private var pendingKeyframe = false
    private let lock = NSLock()
    private let onEncodedFrame: @Sendable (Data, UInt32) -> Void
    private static let startCode = Data([0x00, 0x00, 0x00, 0x01])

    public init(isHevc: Bool = true, onEncodedFrame: @escaping @Sendable (Data, UInt32) -> Void) {
        self.isHevc = isHevc
        self.onEncodedFrame = onEncodedFrame
    }

    public func configure(profile: DisplayProfile, orientation: DisplayOrientation) throws {
        stop()
        let dims = profile.dimensions(for: orientation)
        let codecIsHevc = (profile.codec == .h265) && isHevc
        activeCodecIsHevc = codecIsHevc
        let codecType: CMVideoCodecType = codecIsHevc ? kCMVideoCodecType_HEVC : kCMVideoCodecType_H264

        var session: VTCompressionSession?
        let status = VTCompressionSessionCreate(
            allocator: kCFAllocatorDefault,
            width: Int32(dims.width),
            height: Int32(dims.height),
            codecType: codecType,
            encoderSpecification: nil,
            imageBufferAttributes: nil,
            compressedDataAllocator: nil,
            outputCallback: nil,
            refcon: nil,
            compressionSessionOut: &session
        )

        guard status == noErr, let session else {
            throw NSError(domain: "app.drivelink.cast.encoder", code: Int(status))
        }

        VTSessionSetProperty(session, key: kVTCompressionPropertyKey_RealTime, value: kCFBooleanTrue)
        VTSessionSetProperty(session, key: kVTCompressionPropertyKey_AllowFrameReordering, value: kCFBooleanFalse)
        VTSessionSetProperty(
            session,
            key: kVTCompressionPropertyKey_ExpectedFrameRate,
            value: NSNumber(value: profile.targetFps)
        )
        VTSessionSetProperty(
            session,
            key: kVTCompressionPropertyKey_AverageBitRate,
            value: NSNumber(value: profile.bitrateMbps * 1_000_000)
        )
        VTSessionSetProperty(
            session,
            key: kVTCompressionPropertyKey_MaxKeyFrameIntervalDuration,
            value: NSNumber(value: 2)
        )

        VTCompressionSessionPrepareToEncodeFrames(session)
        compressionSession = session
    }

    public func requestKeyFrame() {
        lock.lock()
        pendingKeyframe = true
        lock.unlock()
    }

    public func encode(sampleBuffer: CMSampleBuffer, forceKeyframe: Bool = false) {
        guard let session = compressionSession,
              let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            return
        }

        lock.lock()
        let shouldForce = forceKeyframe || pendingKeyframe
        pendingKeyframe = false
        lock.unlock()

        let pts = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        let duration = CMSampleBufferGetDuration(sampleBuffer)
        var frameProps: CFDictionary?
        if shouldForce {
            frameProps = [kVTEncodeFrameOptionKey_ForceKeyFrame: kCFBooleanTrue] as CFDictionary
        }

        let codecIsHevc = activeCodecIsHevc
        VTCompressionSessionEncodeFrame(
            session,
            imageBuffer: pixelBuffer,
            presentationTimeStamp: pts,
            duration: duration,
            frameProperties: frameProps,
            infoFlagsOut: nil
        ) { [onEncodedFrame, codecIsHevc] status, _, encodedBuffer in
            guard status == noErr, let encodedBuffer else { return }
            guard let annexB = Self.convertSampleBufferToAnnexB(encodedBuffer, isHevc: codecIsHevc),
                  !annexB.isEmpty else {
                return
            }
            let seconds = CMTimeGetSeconds(pts)
            let rawUs = seconds.isFinite ? Int64((seconds * 1_000_000.0).rounded()) : 0
            let ptsUs = UInt32(truncatingIfNeeded: rawUs)
            onEncodedFrame(annexB, ptsUs)
        }
    }

    public func stop() {
        if let session = compressionSession {
            VTCompressionSessionCompleteFrames(session, untilPresentationTimeStamp: .invalid)
            VTCompressionSessionInvalidate(session)
            compressionSession = nil
        }
    }

    private static func convertSampleBufferToAnnexB(_ sampleBuffer: CMSampleBuffer, isHevc: Bool) -> Data? {
        var result = Data()

        let attachments = CMSampleBufferGetSampleAttachmentsArray(sampleBuffer, createIfNecessary: false) as? [[CFString: Any]]
        let isKeyframe = !(attachments?.first?[kCMSampleAttachmentKey_NotSync] as? Bool ?? false)

        if isKeyframe, let formatDesc = CMSampleBufferGetFormatDescription(sampleBuffer) {
            if isHevc {
                for index in 0..<3 {
                    var ptr: UnsafePointer<UInt8>?
                    var size = 0
                    if CMVideoFormatDescriptionGetHEVCParameterSetAtIndex(
                        formatDesc,
                        parameterSetIndex: index,
                        parameterSetPointerOut: &ptr,
                        parameterSetSizeOut: &size,
                        parameterSetCountOut: nil,
                        nalUnitHeaderLengthOut: nil
                    ) == noErr, let ptr, size > 0 {
                        result.append(startCode)
                        result.append(ptr, count: size)
                    }
                }
            } else {
                for index in 0..<2 {
                    var ptr: UnsafePointer<UInt8>?
                    var size = 0
                    if CMVideoFormatDescriptionGetH264ParameterSetAtIndex(
                        formatDesc,
                        parameterSetIndex: index,
                        parameterSetPointerOut: &ptr,
                        parameterSetSizeOut: &size,
                        parameterSetCountOut: nil,
                        nalUnitHeaderLengthOut: nil
                    ) == noErr, let ptr, size > 0 {
                        result.append(startCode)
                        result.append(ptr, count: size)
                    }
                }
            }
        }

        guard let dataBuffer = CMSampleBufferGetDataBuffer(sampleBuffer) else {
            return result.isEmpty ? nil : result
        }

        var totalLength = 0
        var dataPointer: UnsafeMutablePointer<Int8>?
        guard CMBlockBufferGetDataPointer(
            dataBuffer,
            atOffset: 0,
            lengthAtOffsetOut: nil,
            totalLengthOut: &totalLength,
            dataPointerOut: &dataPointer
        ) == noErr, let dataPointer, totalLength > 4 else {
            return result.isEmpty ? nil : result
        }

        var offset = 0
        while offset + 4 <= totalLength {
            var naluLengthBE: UInt32 = 0
            memcpy(&naluLengthBE, dataPointer.advanced(by: offset), 4)
            let naluLength = Int(UInt32(bigEndian: naluLengthBE))
            offset += 4
            guard naluLength > 0, offset + naluLength <= totalLength else { break }

            result.append(startCode)
            let rawPtr = UnsafeRawPointer(dataPointer.advanced(by: offset)).assumingMemoryBound(to: UInt8.self)
            result.append(rawPtr, count: naluLength)
            offset += naluLength
        }

        return result
    }
}
