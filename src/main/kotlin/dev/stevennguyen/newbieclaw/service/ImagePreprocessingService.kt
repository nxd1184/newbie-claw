package dev.stevennguyen.newbieclaw.service

import dev.stevennguyen.newbieclaw.domain.invoice.PreprocessingStep
import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import kotlin.math.*

@Service
class ImagePreprocessingService {

    fun preprocessImage(
        image: BufferedImage,
        steps: List<PreprocessingStep>,
        denoiseStrength: Int = 3,
        contrastFactor: Double = 1.5
    ): BufferedImage {
        var processedImage = image
        
        for (step in steps) {
            processedImage = when (step) {
                PreprocessingStep.DESKEW -> deskewImage(processedImage)
                PreprocessingStep.DENOISE -> denoiseImage(processedImage, denoiseStrength)
                PreprocessingStep.ENHANCE_CONTRAST -> enhanceContrast(processedImage, contrastFactor)
                PreprocessingStep.BINARIZE -> binarizeImage(processedImage)
                PreprocessingStep.SCALE -> scaleImage(processedImage, 1.5)
            }
        }
        
        return processedImage
    }
    
    fun deskewImage(image: BufferedImage): BufferedImage {
        val angle = detectSkewAngle(image)
        
        if (abs(angle) < 0.5) {
            return image
        }
        
        println("    🔄 Deskewing image by ${String.format("%.2f", angle)} degrees")
        
        val radians = Math.toRadians(angle)
        val cos = abs(cos(radians))
        val sin = abs(sin(radians))
        
        val newWidth = (image.width * cos + image.height * sin).toInt()
        val newHeight = (image.height * cos + image.width * sin).toInt()
        
        val rotated = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = rotated.createGraphics()
        
        g2d.color = Color.WHITE
        g2d.fillRect(0, 0, newWidth, newHeight)
        
        g2d.translate(newWidth / 2.0, newHeight / 2.0)
        g2d.rotate(Math.toRadians(angle))
        g2d.translate(-image.width / 2.0, -image.height / 2.0)
        g2d.drawImage(image, 0, 0, null)
        g2d.dispose()
        
        return rotated
    }
    
    private fun detectSkewAngle(image: BufferedImage): Double {
        val gray = convertToGrayscale(image)
        val edges = detectEdges(gray)
        
        val angles = mutableListOf<Double>()
        val step = 5
        
        for (y in 0 until edges.height step step) {
            for (x in 0 until edges.width - 1 step step) {
                if (edges.getRGB(x, y) == Color.BLACK.rgb) {
                    var nextX = x + step
                    while (nextX < edges.width && edges.getRGB(nextX, y) != Color.BLACK.rgb) {
                        nextX++
                    }
                    
                    if (nextX < edges.width) {
                        val angle = atan2((y - y).toDouble(), (nextX - x).toDouble())
                        angles.add(Math.toDegrees(angle))
                    }
                }
            }
        }
        
        return if (angles.isNotEmpty()) {
            angles.average().coerceIn(-10.0, 10.0)
        } else {
            0.0
        }
    }
    
    fun denoiseImage(image: BufferedImage, strength: Int): BufferedImage {
        println("    🧹 Denoising image (strength: $strength)")
        
        val size = strength * 2 + 1
        val kernelData = FloatArray(size * size) { 1.0f / (size * size) }
        val kernel = Kernel(size, size, kernelData)
        val op = ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null)
        
        return op.filter(image, null)
    }
    
    fun enhanceContrast(image: BufferedImage, factor: Double): BufferedImage {
        println("    ✨ Enhancing contrast (factor: $factor)")
        
        val enhanced = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val color = Color(rgb)
                
                val r = ((color.red - 128) * factor + 128).toInt().coerceIn(0, 255)
                val g = ((color.green - 128) * factor + 128).toInt().coerceIn(0, 255)
                val b = ((color.blue - 128) * factor + 128).toInt().coerceIn(0, 255)
                
                enhanced.setRGB(x, y, Color(r, g, b).rgb)
            }
        }
        
        return enhanced
    }
    
    fun binarizeImage(image: BufferedImage): BufferedImage {
        println("    ⚫⚪ Binarizing image (Otsu's method)")
        
        val gray = convertToGrayscale(image)
        val threshold = calculateOtsuThreshold(gray)
        
        val binary = BufferedImage(gray.width, gray.height, BufferedImage.TYPE_BYTE_BINARY)
        
        for (y in 0 until gray.height) {
            for (x in 0 until gray.width) {
                val pixel = Color(gray.getRGB(x, y)).red
                val binaryPixel = if (pixel > threshold) Color.WHITE else Color.BLACK
                binary.setRGB(x, y, binaryPixel.rgb)
            }
        }
        
        return binary
    }
    
    private fun calculateOtsuThreshold(image: BufferedImage): Int {
        val histogram = IntArray(256)
        
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val pixel = Color(image.getRGB(x, y)).red
                histogram[pixel]++
            }
        }
        
        val total = image.width * image.height
        var sum = 0.0
        for (i in 0..255) {
            sum += i * histogram[i]
        }
        
        var sumB = 0.0
        var wB = 0
        var wF: Int
        var maxVariance = 0.0
        var threshold = 0
        
        for (t in 0..255) {
            wB += histogram[t]
            if (wB == 0) continue
            
            wF = total - wB
            if (wF == 0) break
            
            sumB += t * histogram[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            
            val variance = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
            
            if (variance > maxVariance) {
                maxVariance = variance
                threshold = t
            }
        }
        
        return threshold
    }
    
    fun scaleImage(image: BufferedImage, scaleFactor: Double): BufferedImage {
        val newWidth = (image.width * scaleFactor).toInt()
        val newHeight = (image.height * scaleFactor).toInt()
        
        val scaled = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = scaled.createGraphics()
        g2d.drawImage(image, 0, 0, newWidth, newHeight, null)
        g2d.dispose()
        
        return scaled
    }
    
    private fun convertToGrayscale(image: BufferedImage): BufferedImage {
        val gray = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
        val g2d = gray.createGraphics()
        g2d.drawImage(image, 0, 0, null)
        g2d.dispose()
        return gray
    }
    
    private fun detectEdges(image: BufferedImage): BufferedImage {
        val sobelX = floatArrayOf(
            -1f, 0f, 1f,
            -2f, 0f, 2f,
            -1f, 0f, 1f
        )
        
        val kernel = Kernel(3, 3, sobelX)
        val op = ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null)
        
        return op.filter(image, null)
    }
}
