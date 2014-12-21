package me.lightspeed7.mongoFS.tutorial.image

import org.bson.types.ObjectId

import play.api.libs.json.{ Format, JsError, JsString, JsSuccess, JsValue, Json }

// Image Model
case class Image(
  _id: ObjectId,
  imageUrl: String,
  thumbUrl: Option[String],
  mediumUrl: Option[String])

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

}
