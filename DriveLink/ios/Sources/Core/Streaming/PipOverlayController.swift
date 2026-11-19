import AVFoundation
import AVKit
import CoreVideo
import Foundation

#if canImport(UIKit)
import UIKit
#endif

/// Provides a real Picture-in-Picture floating status HUD on iOS using AVSampleBufferDisplayLayer
/// so the user sees live BYD connection state and controls while using other apps.
@MainActor
public final class PipOverlayController: NSObject, ObservableObject {

    @Published public private(set) var isPipActive = false
    @Published public private(set) var isPaused = false

    public let displayLayer = AVSampleBufferDisplayLayer()
    private var pipController: AVPictureInPictureController?
    private var renderTimer: Timer?
    private var statusText: String = "BYD • LIVE"

    public func configure() {
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return }
        #if !os(macOS)
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        #endif

        let source = AVPictureInPictureController.ContentSource(
            sampleBufferDisplayLayer: displayLayer,
            playbackDelegate: self
        )
        let controller = AVPictureInPictureController(contentSource: source)
        controller.delegate = self
        #if !os(macOS)
        controller.canStartPictureInPictureAutomaticallyFromInline = true
        #endif
        pipController = controller

        enqueueStatusFrame()
        renderTimer?.invalidate()
        renderTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.enqueueStatusFrame() }
        }
    }

    public func updateStatus(_ text: String) {
        statusText = text
        enqueueStatusFrame()
    }

    public func startPip() {
        if pipController == nil { configure() }
        enqueueStatusFrame()
        pipController?.startPictureInPicture()
    }

    public func stopPip() {
        renderTimer?.invalidate()
        renderTimer = nil
        pipController?.stopPictureInPicture()
    }

    private func enqueueStatusFrame() {
        guard let sampleBuffer = makeStatusSampleBuffer(text: statusText) else { return }
        if displayLayer.status == .failed {
            displayLayer.flush()
        }
        displayLayer.enqueue(sampleBuffer)
    }

    private func makeStatusSampleBuffer(text: String) -> CMSampleBuffer? {
        let width = 320
        let height = 140
        var pixelBuffer: CVPixelBuffer?
        let attrs: [CFString: Any] = [
            kCVPixelBufferCGImageCompatibilityKey: true,
            kCVPixelBufferCGBitmapContextCompatibilityKey: true
        ]
        guard CVPixelBufferCreate(
            kCFAllocatorDefault,
            width,
            height,
            kCVPixelFormatType_32BGRA,
            attrs as CFDictionary,
            &pixelBuffer
        ) == kCVReturnSuccess, let buffer = pixelBuffer else {
            return nil
        }

        CVPixelBufferLockBaseAddress(buffer, [])
        if let baseAddress = CVPixelBufferGetBaseAddress(buffer) {
            let colorSpace = CGColorSpaceCreateDeviceRGB()
            if let ctx = CGContext(
                data: baseAddress,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: CVPixelBufferGetBytesPerRow(buffer),
                space: colorSpace,
                bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue
            ) {
                ctx.setFillColor(red: 0.03, green: 0.04, blue: 0.06, alpha: 1.0)
                ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
                ctx.setStrokeColor(red: 0.0, green: 0.83, blue: 1.0, alpha: 1.0)
                ctx.setLineWidth(3)
                ctx.stroke(CGRect(x: 4, y: 4, width: width - 8, height: height - 8))

                #if canImport(UIKit)
                UIGraphicsPushContext(ctx)
                let attrsText: [NSAttributedString.Key: Any] = [
                    .font: UIFont.monospacedSystemFont(ofSize: 20, weight: .bold),
                    .foregroundColor: UIColor(red: 0.06, green: 0.73, blue: 0.51, alpha: 1.0)
                ]
                (text as NSString).draw(at: CGPoint(x: 24, y: 56), withAttributes: attrsText)
                UIGraphicsPopContext()
                #endif
            }
        }
        CVPixelBufferUnlockBaseAddress(buffer, [])

        var formatDesc: CMVideoFormatDescription?
        CMVideoFormatDescriptionCreateForImageBuffer(
            allocator: kCFAllocatorDefault,
            imageBuffer: buffer,
            formatDescriptionOut: &formatDesc
        )
        guard let formatDesc else { return nil }

        var timing = CMSampleTimingInfo(
            duration: CMTime(value: 1, timescale: 1),
            presentationTimeStamp: CMClockGetTime(CMClockGetHostTimeClock()),
            decodeTimeStamp: .invalid
        )
        var sampleBuffer: CMSampleBuffer?
        CMSampleBufferCreateReadyWithImageBuffer(
            allocator: kCFAllocatorDefault,
            imageBuffer: buffer,
            formatDescription: formatDesc,
            sampleTiming: &timing,
            sampleBufferOut: &sampleBuffer
        )
        return sampleBuffer
    }
}

extension PipOverlayController: AVPictureInPictureControllerDelegate {
    nonisolated public func pictureInPictureControllerDidStartPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        Task { @MainActor in self.isPipActive = true }
    }

    nonisolated public func pictureInPictureControllerDidStopPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        Task { @MainActor in self.isPipActive = false }
    }
}

extension PipOverlayController: AVPictureInPictureSampleBufferPlaybackDelegate {
    nonisolated public func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, setPlaying playing: Bool) {
        Task { @MainActor in self.isPaused = !playing }
    }

    nonisolated public func pictureInPictureControllerTimeRangeForPlayback(_ pictureInPictureController: AVPictureInPictureController) -> CMTimeRange {
        CMTimeRange(start: .zero, duration: .positiveInfinity)
    }

    nonisolated public func pictureInPictureControllerIsPlaybackPaused(_ pictureInPictureController: AVPictureInPictureController) -> Bool {
        false
    }

    nonisolated public func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, didTransitionToRenderSize newRenderSize: CMVideoDimensions) {}

    nonisolated public func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, skipByInterval skipInterval: CMTime) async {}
}
