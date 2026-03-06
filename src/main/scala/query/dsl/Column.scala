package query.dsl

import scala.annotation.nowarn
import scala.reflect.ClassTag

final case class Column[+T] private (private val map: Map[ClassTag[?], Any])

object Column {
  implicit class ColumnOps[C <: Column[?]](val self: C) extends AnyVal {
    def ++[A <: Column[?]](
        that: A
      )(implicit
        @nowarn("msg=never used") ev: C <:!< A
      ): C & A =
      new Column(self.map + that.map.head).asInstanceOf[C & A]

    def column[A <: IndexColumn[?]: ClassTag](
        implicit
        ev: C <:< Column[A]
      ): A =
      self.map(implicitly[ClassTag[A]]).asInstanceOf[A]

  }

  def apply[A: ClassTag](a: A): Column[A] =
    new Column(Map(implicitly[ClassTag[A]] -> a))

}
