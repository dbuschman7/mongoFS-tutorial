package me.lightspeed7.mongoFS.tutorial.image

import me.lightspeed7.mongofs.MongoFile
import org.imgscalr._
import org.imgscalr.Scalr._
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import me.lightspeed7.mongofs.MongoFileWriter
import scala.util.Try

object ThumbnailGenerator {

  def createThumbnail(source: MongoFile, sink: MongoFileWriter, sideLength: Int) = {
    writeInto(sink, scaled(from(source), sideLength))
  }

  private[image] def scaled(image: BufferedImage, sideLength: Int): BufferedImage = {
    Scalr.resize(image, Scalr.Method.AUTOMATIC, Scalr.Mode.AUTOMATIC, sideLength, Scalr.OP_ANTIALIAS);
  }

  private[image] def from(file: MongoFile): BufferedImage = {
    ImageIO.read(file.getInputStream)
  }

  private[image] def writeInto(writer: MongoFileWriter, img: BufferedImage): MongoFile = {

    val out = writer.getOutputStream
    Try(ImageIO.write(img, "png", out)).map(_ => out.close)
    writer.getMongoFile
  }

}