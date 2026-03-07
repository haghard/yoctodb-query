import scala.meta._

object CompanionFunctions {

  def buildFilterableTerm(termType: Type.Name, termTypeParamType: Type.Name): Defn.Val =
    q"""
      val $$: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {

        def =:=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.eq(name, UnsignedByteArrays.from(v))

        def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
          QueryBuilder.in(
            name,
            vs.toSeq.map(UnsignedByteArrays.from(_)):_*
          )
      }
    """

  def buildSortableTerm(termType: Type.Name, termTypeParamType: Type.Name): Defn.Val =
    q"""
      val $$: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
        def desc(): com.yandex.yoctodb.query.Order = QueryBuilder.desc(name)
        def asc(): com.yandex.yoctodb.query.Order = QueryBuilder.asc(name)
      }
    """

  def buildFilterableNumTerm(termType: Type.Name, termTypeParamType: Type.Name): Defn.Val =
    q"""
      val $$: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {

        def =:=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.eq(name, UnsignedByteArrays.from(v))

        def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
          QueryBuilder.in(name, vs.toSeq.map(UnsignedByteArrays.from(_)):_*)

        def >(v: ${termTypeParamType}): TermCondition =
         QueryBuilder.gt(name, UnsignedByteArrays.from(v))

        def >=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.gte(name, UnsignedByteArrays.from(v))

        def <(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.lt(name, UnsignedByteArrays.from(v))

        def <=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.lte(name, UnsignedByteArrays.from(v))
      }
    """
}
