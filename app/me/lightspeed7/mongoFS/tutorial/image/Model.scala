package me.lightspeed7.mongoFS.tutorial.image

import org.bson.types.ObjectId
import play.api.libs.json.{ Format, JsError, JsString, JsSuccess, JsValue, Json }
import com.mongodb.DBObject

// Image Model
case class Image(
  _id: ObjectId,
  imageUrl: String,
  thumbUrl: Option[String],
  mediumUrl: Option[String],
  description: Option[String],
  tooltip: Option[String] //
  )

case class UiImage(
  id: String,
  description: Option[String],
  tooltip: Option[String] //
  )

object Image {

  // JSON formatting
  implicit val objectIdFormat: Format[ObjectId] = new Format[ObjectId] {

    def reads(json: JsValue) = {
      json match {
        case jsString: JsString => {
          if (ObjectId.isValid(jsString.value)) JsSuccess(new ObjectId(jsString.value))
          else JsError("Invalid ObjectId")
        }
        case other => JsError("Can't parse json path as an ObjectId. Json content = " + other.toString())
      }
    }

    def writes(oId: ObjectId): JsValue = {
      JsString(oId.toString)
    }

  }
  implicit val imageFmt = Json.format[Image]

  def fields() = {
    val fields = (Map[String, Any]() /: Image.getClass.getDeclaredFields) { (a, f) =>
      f.setAccessible(true)
      a + (f.getName -> f.get(this))
    }
  }

  implicit def fromMongoDB(obj: DBObject): Image = {
    val id: ObjectId = obj.get("_id").asInstanceOf[ObjectId]
    val imageUrl = obj.get("imageUrl").toString
    val mediumUrl = if (obj.get("mediumUrl") != null) Some(obj.get("mediumUrl").toString) else None
    val thumbUrl = if (obj.get("thumbUrl") != null) Some(obj.get("thumbUrl").toString) else None
    val description = if (obj.get("description") != null) Some(obj.get("description").toString) else None
    val tooltip = if (obj.get("tooltip") != null) Some(obj.get("tooltip").toString) else None

    Image(id, imageUrl, thumbUrl, mediumUrl, description, tooltip)
  }

}

object UiImage {
  implicit val UiImageFmt = Json.format[UiImage]

  implicit def fromMongoDB(obj: Image): UiImage = {
    UiImage(obj._id.toString(), obj.description, obj.tooltip)
  }
}
