import AppKit
import Foundation

struct Palette {
    static let midnight = NSColor(calibratedRed: 23/255, green: 43/255, blue: 58/255, alpha: 1)
    static let coral = NSColor(calibratedRed: 255/255, green: 107/255, blue: 87/255, alpha: 1)
    static let chalk = NSColor(calibratedRed: 247/255, green: 244/255, blue: 238/255, alpha: 1)
    static let night = NSColor(calibratedRed: 13/255, green: 23/255, blue: 31/255, alpha: 1)
}

enum Background {
    case transparent, chalk, night
}

func drawMark(in rect: CGRect, background: Background, monochrome: Bool = false) {
    switch background {
    case .transparent: NSColor.clear.setFill()
    case .chalk: Palette.chalk.setFill()
    case .night: Palette.night.setFill()
    }
    rect.fill()

    let scale = min(rect.width, rect.height) / 512
    let ox = rect.midX - 256 * scale
    let oy = rect.midY - 256 * scale
    let transform = CGAffineTransform(translationX: ox, y: oy).scaledBy(x: scale, y: scale)

    func path(_ points: [(CGFloat, CGFloat)], color: NSColor) {
        let bezier = NSBezierPath()
        bezier.lineWidth = 38 * scale
        bezier.lineCapStyle = .round
        bezier.lineJoinStyle = .round
        bezier.move(to: CGPoint(x: points[0].0, y: 512 - points[0].1).applying(transform))
        bezier.line(to: CGPoint(x: points[1].0, y: 512 - points[1].1).applying(transform))
        bezier.curve(
            to: CGPoint(x: points[4].0, y: 512 - points[4].1).applying(transform),
            controlPoint1: CGPoint(x: points[2].0, y: 512 - points[2].1).applying(transform),
            controlPoint2: CGPoint(x: points[3].0, y: 512 - points[3].1).applying(transform)
        )
        bezier.line(to: CGPoint(x: points[5].0, y: 512 - points[5].1).applying(transform))
        color.setStroke()
        bezier.stroke()
    }

    let primary = background == .night ? NSColor.white : Palette.midnight
    let accent = monochrome ? primary : Palette.coral
    path([(74,196), (202,196), (238,196), (249,132), (293,132), (408,132)], color: primary)
    path([(74,316), (202,316), (238,316), (249,252), (293,252), (369,252)], color: accent)

    let dot = NSBezierPath(ovalIn: CGRect(x: 402, y: 512 - 273, width: 42, height: 42).applying(transform))
    accent.setFill()
    dot.fill()
}

func writePNG(size: Int, background: Background, monochrome: Bool = false, to url: URL) throws {
    let hasAlpha = background == .transparent
    let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
    let alphaInfo: CGImageAlphaInfo = hasAlpha ? .premultipliedLast : .noneSkipLast
    guard let cgContext = CGContext(
        data: nil,
        width: size,
        height: size,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: colorSpace,
        bitmapInfo: alphaInfo.rawValue
    ) else {
        throw NSError(domain: "AJUSTEBrand", code: 1)
    }

    let context = NSGraphicsContext(cgContext: cgContext, flipped: false)
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = context
    context.imageInterpolation = NSImageInterpolation.high
    drawMark(in: CGRect(x: 0, y: 0, width: size, height: size), background: background, monochrome: monochrome)
    context.flushGraphics()
    NSGraphicsContext.restoreGraphicsState()

    guard let cgImage = cgContext.makeImage(),
          let data = NSBitmapImageRep(cgImage: cgImage).representation(using: NSBitmapImageRep.FileType.png, properties: [:]) else {
        throw NSError(domain: "AJUSTEBrand", code: 2)
    }
    try data.write(to: url)
}

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let appIcon = root.appendingPathComponent("BioTrack/Assets.xcassets/AppIcon.appiconset")
let sizes: [(String, Int)] = [
    ("AppIcon-1024.png", 1024), ("AppIcon-20.png", 20), ("AppIcon-20@2x.png", 40),
    ("AppIcon-20@3x.png", 60), ("AppIcon-29.png", 29), ("AppIcon-29@2x.png", 58),
    ("AppIcon-29@3x.png", 87), ("AppIcon-40.png", 40), ("AppIcon-40@2x.png", 80),
    ("AppIcon-40@3x.png", 120), ("AppIcon-60@2x.png", 120), ("AppIcon-60@3x.png", 180),
    ("AppIcon-76.png", 76), ("AppIcon-76@2x.png", 152), ("AppIcon-83.5@2x.png", 167)
]

for (name, size) in sizes {
    try writePNG(size: size, background: .chalk, to: appIcon.appendingPathComponent(name))
}

let assets = root.appendingPathComponent("BioTrack/Assets.xcassets")
try writePNG(size: 1024, background: .transparent, to: assets.appendingPathComponent("OnboardingLogo.imageset/onboarding-logo-light.png"))
try writePNG(size: 1024, background: .night, to: assets.appendingPathComponent("OnboardingLogo.imageset/onboarding-logo-dark.png"))
try writePNG(size: 1024, background: .transparent, to: assets.appendingPathComponent("TitleLogo.imageset/title-logo-light.png"))
try writePNG(size: 1024, background: .night, to: assets.appendingPathComponent("TitleLogo.imageset/title-logo-dark.png"))
try writePNG(size: 640, background: .transparent, to: assets.appendingPathComponent("HeaderLogo.imageset/header-logo.png"))

let brand = root.appendingPathComponent("Brand")
try writePNG(size: 1024, background: .chalk, to: brand.appendingPathComponent("ajuste-app-icon-1024.png"))
try writePNG(size: 1024, background: .chalk, monochrome: true, to: brand.appendingPathComponent("ajuste-social-avatar-1024.png"))
print("AJUSTE brand assets generated.")
