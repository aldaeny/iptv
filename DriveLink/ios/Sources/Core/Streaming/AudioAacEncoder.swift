import AudioToolbox
import CoreMedia
import Foundation

/// Encodes ReplayKit app audio PCM sample buffers into AAC-LC 48kHz stereo frames
/// using Apple AudioToolbox.
public final class AudioAacEncoder: @unchecked Sendable {

    private var converter: AudioConverterRef?
    private var sourceChannels: UInt32 = 2
    private let onEncodedAac: @Sendable (Data, UInt32) -> Void
    private var outputBuffer = [UInt8](repeating: 0, count: 4096)

    public init(onEncodedAac: @escaping @Sendable (Data, UInt32) -> Void) {
        self.onEncodedAac = onEncodedAac
    }

    public func encode(sampleBuffer: CMSampleBuffer) {
        guard let formatDesc = CMSampleBufferGetFormatDescription(sampleBuffer),
              let asbdPtr = CMAudioFormatDescriptionGetStreamBasicDescription(formatDesc) else {
            return
        }

        if converter == nil {
            setupConverter(sourceAsbd: asbdPtr.pointee)
        }
        guard let converter else { return }

        var blockBuffer: CMBlockBuffer?
        var bufferList = AudioBufferList()
        let status = CMSampleBufferGetAudioBufferListWithRetainedBlockBuffer(
            sampleBuffer,
            bufferListSizeNeededOut: nil,
            bufferListOut: &bufferList,
            bufferListSize: MemoryLayout<AudioBufferList>.size,
            blockBufferAllocator: kCFAllocatorDefault,
            blockBufferMemoryAllocator: kCFAllocatorDefault,
            flags: 0,
            blockBufferOut: &blockBuffer
        )
        guard status == noErr, let pcmData = bufferList.mBuffers.mData else { return }

        var context = PcmInputContext(
            data: pcmData,
            byteSize: bufferList.mBuffers.mDataByteSize,
            channels: sourceChannels,
            consumed: false
        )

        var ioOutputPackets: UInt32 = 1
        var encodedData: Data?
        let convertStatus: OSStatus = outputBuffer.withUnsafeMutableBytes { rawOutBuf in
            var outAudioBuffer = AudioBuffer(
                mNumberChannels: 2,
                mDataByteSize: UInt32(rawOutBuf.count),
                mData: rawOutBuf.baseAddress
            )
            var outList = AudioBufferList(mNumberBuffers: 1, mBuffers: outAudioBuffer)

            let status = AudioConverterFillComplexBuffer(
                converter,
                { _, ioNumberDataPackets, ioData, _, inUserData -> OSStatus in
                    guard let userData = inUserData else { return -1 }
                    let ctx = userData.assumingMemoryBound(to: PcmInputContext.self)
                    if ctx.pointee.consumed {
                        ioNumberDataPackets.pointee = 0
                        return noErr
                    }
                    ctx.pointee.consumed = true
                    ioData.pointee.mNumberBuffers = 1
                    ioData.pointee.mBuffers.mData = ctx.pointee.data
                    ioData.pointee.mBuffers.mDataByteSize = ctx.pointee.byteSize
                    ioData.pointee.mBuffers.mNumberChannels = ctx.pointee.channels
                    return noErr
                },
                &context,
                &ioOutputPackets,
                &outList,
                nil
            )

            if status == noErr, ioOutputPackets > 0, let base = rawOutBuf.baseAddress {
                let encodedCount = Int(outList.mBuffers.mDataByteSize)
                if encodedCount > 0 {
                    encodedData = Data(bytes: base, count: encodedCount)
                }
            }
            return status
        }

        if convertStatus == noErr, let data = encodedData {
            let pts = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
            let seconds = CMTimeGetSeconds(pts)
            let rawUs = seconds.isFinite ? Int64((seconds * 1_000_000.0).rounded()) : 0
            let ptsUs = UInt32(truncatingIfNeeded: rawUs)
            onEncodedAac(data, ptsUs)
        }
    }

    private func setupConverter(sourceAsbd: AudioStreamBasicDescription) {
        sourceChannels = max(1, sourceAsbd.mChannelsPerFrame)
        var inDesc = sourceAsbd
        var outDesc = AudioStreamBasicDescription(
            mSampleRate: 48_000,
            mFormatID: kAudioFormatMPEG4AAC,
            mFormatFlags: UInt32(MPEG4ObjectID.AAC_LC.rawValue),
            mBytesPerPacket: 0,
            mFramesPerPacket: 1024,
            mBytesPerFrame: 0,
            mChannelsPerFrame: 2,
            mBitsPerChannel: 0,
            mReserved: 0
        )
        var created: AudioConverterRef?
        if AudioConverterNew(&inDesc, &outDesc, &created) == noErr {
            converter = created
        }
    }

    public func stop() {
        if let converter {
            AudioConverterDispose(converter)
            self.converter = nil
        }
    }

    private struct PcmInputContext {
        var data: UnsafeMutableRawPointer
        var byteSize: UInt32
        var channels: UInt32
        var consumed: Bool
    }
}
