package query.dsl

abstract class <:!<[A, B] extends Serializable
object <:!< {
  implicit def nsub[A, B]: A <:!< B = new <:!<[A, B] {}

  @scala.annotation.implicitAmbiguous("Cannot add ${B} to ${A} !")
  implicit def nsubAmbig1[A, B >: A]: A <:!< B = sys.error("Unexpected call")
  implicit def nsubAmbig2[A, B >: A]: A <:!< B = sys.error("Unexpected call")

}
