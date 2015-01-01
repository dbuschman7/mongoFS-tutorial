package me.lightspeed7.mongoFS.tutorial.image

import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.image.{ AffineTransformOp, BufferedImage }
import java.io.InputStream

import scala.util.Try

import org.imgscalr.Scalr

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.{ Directory, Metadata, MetadataException }
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.jpeg.JpegDirectory

import javax.imageio.ImageIO
import me.lightspeed7.mongofs.{ MongoFile, MongoFileWriter }

object ThumbnailService {

  case class ImageInformation(orientation: Int, width: Int, height: Int)

  def createThumbnail(source: MongoFile, sink: MongoFileWriter, sideLength: Int): MongoFile = {
    val original = from(source)
    val thumb = scaled(original, sideLength)
    val transform = getExifTransformation(readImageInformation(source.getInputStream))
    val transformed = transformImage(thumb, transform)
    writeInto(sink, transformed)
  }

  private[image] def readImageInformation(imageStream: InputStream): ImageInformation = {
    val metadata: Metadata = ImageMetadataReader.readMetadata(imageStream)
    val directory: Directory = metadata.getDirectory(classOf[ExifIFD0Directory])

    val jpegDirectory: JpegDirectory = metadata.getDirectory(classOf[JpegDirectory])

    var orientation: Int = getOrientation(jpegDirectory);
    return new ImageInformation(orientation, jpegDirectory.getImageWidth(), jpegDirectory.getImageHeight());
  }

  private[image] def getOrientation(directory: JpegDirectory): Int = {
    try {
      directory.getInt(ExifIFD0Directory.TAG_ORIENTATION)
    } catch {
      case me: MetadataException => println("Cound not detect orientation"); 1
    }
  }

  private[image] def getExifTransformation(info: ImageInformation): AffineTransform = {
    val t = new AffineTransform
    println(s"Orientation = ${info.orientation}")
    info.orientation match {
      case 1 => t
      case 2 => { t.scale(-1.0, 1.0); t.translate(-info.width, 0); t }
      case 3 => { t.translate(info.width, info.height); t.rotate(Math.PI); t }
      case 4 => { t.scale(1.0, -1.0); t.translate(0, -info.height); t }
      case 5 => { t.rotate(-Math.PI / 2); t.scale(-1.0, 1.0); t }
      case 6 => { t.translate(info.height, 0); t.rotate(Math.PI / 2); t }
      case 7 => { t.scale(-1.0, 1.0); t.translate(-info.height, 0); t.translate(0, info.width); t.rotate(3 * Math.PI / 2); t }
      case 8 => { t.translate(0, info.width); t.rotate(3 * Math.PI / 2); t }
    }
  }

  private[image] def transformImage(image: BufferedImage, transform: AffineTransform): BufferedImage = {
    val op: AffineTransformOp = new AffineTransformOp(transform, AffineTransformOp.TYPE_BICUBIC)
    val f = if (image.getType() == BufferedImage.TYPE_BYTE_GRAY) image.getColorModel() else null
    val destinationImage: BufferedImage = op.createCompatibleDestImage(image, f);

    val g = destinationImage.createGraphics();
    g.setBackground(Color.WHITE);
    g.clearRect(0, 0, destinationImage.getWidth(), destinationImage.getHeight());
    op.filter(image, destinationImage); ;
  }

  private[image] def from(file: MongoFile): BufferedImage = {
    ImageIO.read(file.getInputStream)
  }

  private[image] def scaled(image: BufferedImage, sideLength: Int): BufferedImage = {
    val mode = if (image.getHeight > image.getWidth) Scalr.Mode.FIT_TO_HEIGHT else Scalr.Mode.FIT_TO_WIDTH
    Scalr.resize(image, Scalr.Method.AUTOMATIC, mode, sideLength, Scalr.OP_ANTIALIAS);
  }
  private[image] def writeInto(writer: MongoFileWriter, img: BufferedImage): MongoFile = {

    val out = writer.getOutputStream
    Try(ImageIO.write(img, "png", out)).map(_ => out.close)
    writer.getMongoFile
  }
}
