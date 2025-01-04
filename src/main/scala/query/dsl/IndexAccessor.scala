package query.dsl

import com.yandex.yoctodb.query.{ QueryBuilder, TermCondition }
import com.yandex.yoctodb.util.{ UnsignedByteArray, UnsignedByteArrays }
import query.dsl.YoctoProjection.mkBtsArr
import zio.schema.{ Schema, TypeId }

import scala.annotation.nowarn
import scala.annotation.implicitNotFound

@implicitNotFound("Primitive type ${T} isn't supported")
sealed trait PrimitiveValueType[T]

object PrimitiveValueType {
  implicit object Int_ extends PrimitiveValueType[Int]
  implicit object Long_ extends PrimitiveValueType[Long]
  implicit object String_ extends PrimitiveValueType[String]

}

trait IndexAccessor {
  type Columns
  val columns: Columns

}

object IndexAccessor {
  type Lens[F, S, A] = YoctoProjection.Lens[F, S, A]
  type Prism[F, S, A] = YoctoProjection.Prism[F, S, A]
  type Traversal[S, A] = YoctoProjection.Traversal[S, A]

  type Aux[Columns0] = IndexAccessor {
    type Columns = Columns0
  }

  def apply[A](
      implicit
      S: Schema[A]
    ): IndexAccessor.Aux[S.Accessors[Lens, Prism, Traversal]] =
    new IndexAccessor {
      val accessorBuilder = YoctoProjection

      override type Columns =
        S.Accessors[accessorBuilder.Lens, accessorBuilder.Prism, accessorBuilder.Traversal]

      override val columns: Columns =
        S.makeAccessors(accessorBuilder)

    }

}

object YoctoProjection extends zio.schema.AccessorBuilder {
  type Lens[F, S, A] = TermConditionBuilder[S, A]
  type Prism[F, S, A] = Unit
  type Traversal[S, A] = Unit

  override def makeLens[F, S, A](
      schema: Schema.Record[S],
      term: Schema.Field[S, A],
    ): TermConditionBuilder[S, A] =
    TermConditionBuilder[S, A](schema, List(term.name))

  override def makePrism[F, S, A](
      sum: Schema.Enum[S],
      term: Schema.Case[S, A],
    ): Unit = ()

  override def makeTraversal[S, A](collection: Schema.Collection[S, A], element: Schema[A]): Unit =
    ()

  def mkBtsArr[T: PrimitiveValueType](
      v: T
    )(implicit
      ev: PrimitiveValueType[T]
    ): UnsignedByteArray =
    ev match {
      case PrimitiveValueType.Int_ => UnsignedByteArrays.from(v)
      case PrimitiveValueType.Long_ => UnsignedByteArrays.from(v)
      case PrimitiveValueType.String_ => UnsignedByteArrays.from(v)
    }

}

final case class TermConditionBuilder[S, A](
    schema: Schema.Record[S],
    path: List[String],
    sums: Map[String, TypeId] = Map.empty,
  ) {
  self =>
  def =:=(
      that: A
    )(implicit
      tp: PrimitiveValueType[A]
    ): TermCondition =
    QueryBuilder.eq(path.head, mkBtsArr[A](that))

  def in(
      that: scala.collection.immutable.Set[A]
    )(implicit
      tp: PrimitiveValueType[A]
    ): TermCondition =
    QueryBuilder.in(path.head, that.toSeq.map(mkBtsArr[A](_)): _*)

  def >>(
      that: A
    )(implicit
      tp: PrimitiveValueType[A]
    ): TermCondition =
    QueryBuilder.gt(path.head, mkBtsArr[A](that))

  def <<(
      that: A
    )(implicit
      tp: PrimitiveValueType[A]
    ): TermCondition =
    QueryBuilder.lt(path.head, mkBtsArr[A](that))

  def desc(
      implicit
      @implicitNotFound("Expected Numeric but found ${A}")
      @nowarn
      num: Numeric[A]
    ): com.yandex.yoctodb.query.Order = QueryBuilder.desc(path.head)

  def asc(
      implicit
      @implicitNotFound("Expected Numeric but found ${A}")
      @nowarn
      num: Numeric[A]
    ): com.yandex.yoctodb.query.Order = QueryBuilder.asc(path.head)

}
