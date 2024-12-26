package query.dsl

import scala.annotation.nowarn
import scala.reflect.ClassTag

final case class Col[+T] private (private val map: Map[ClassTag[_], Any])

object Col {
  implicit class HSetOps[C <: Col[_]](self: C) {
    // |+|
    def ++[A <: Col[_]](
        that: A
      )(implicit
        @nowarn("msg=never used") ev: C <:!< A
      ): C & A =
      new Col(self.map + that.map.head).asInstanceOf[C & A]

    def column[A: ClassTag](
        implicit
        ev: C <:< Col[A]
      ): A =
      self.map(implicitly[ClassTag[A]]).asInstanceOf[A]

  }

  def apply[A: ClassTag](a: A): Col[A] =
    new Col(Map(implicitly[ClassTag[A]] -> a))

}
