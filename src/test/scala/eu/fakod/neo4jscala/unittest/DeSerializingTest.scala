package eu.fakod.neo4jscala.unittest

import java.util.UUID

import org.specs2.mutable.SpecificationWithJUnit
import eu.fakod.neo4jscala.{EmbeddedGraphDatabaseServiceProvider, Neo4jWrapper}
import eu.fakod.neo4jscala.util.CaseClassDeserializer
import org.neo4j.graphdb.{Direction, DynamicRelationshipType}
import sys.ShutdownHookThread

/**
 * Test spec to check deserialization and serialization of case classes
 *
 * @author Christopher Schmidt
 */

case class Test(s: String, i: Int, ji: java.lang.Integer, d: Double, l: Long, b: Boolean)

case class Test2(jl: java.lang.Long, jd: java.lang.Double, jb: java.lang.Boolean, nullString: String = null)

case class ArrayTest(ar: Array[String])

case class NotTest(s: String, i: Int, ji: java.lang.Integer, d: Double, l: Long, b: Boolean)

trait PolyBase

case class Poly1(s: String) extends PolyBase

case class Poly2(s: String) extends PolyBase

import CaseClassDeserializer._

class DeSerializingWithoutNeo4jSpec extends SpecificationWithJUnit {

  "De- and Serializing" should {

    "able to create an instance from map" in {
      val m = Map[String, AnyRef]("s" -> "sowas", "i" -> "1", "ji" -> "2", "d" -> (3.3).asInstanceOf[AnyRef], "l" -> "10", "b" -> "true")
      val r = deserialize[Test](m)

      r.s must endWith("sowas")
      r.i must_== (1)
      r.ji must_== (2)
      r.d must_== (3.3)
      r.l must_== (10)
      r.b must_== (true)
    }

    "able to create a map from an instance" in {
      val o = Test("sowas", 1, 2, 3.3, 10, true)
      val resMap = serialize(o)

      resMap.size must_== 6
      resMap.get("d").get mustEqual (3.3)
      resMap.get("b").get mustEqual (true)
    }
  }
}

class DeSerializingSpec extends SpecificationWithJUnit with Neo4jWrapper with EmbeddedGraphDatabaseServiceProvider {

  def neo4jStoreDir = "./target/temp-neo-test2" + UUID.randomUUID()

  "Node" should {

    ShutdownHookThread {
      shutdown(ds)
    }

    "be serializable with Test" in {
      withTx { neo =>
        val o = Test("sowas", 1, 2, 3.3, 10, true)
        val node = createNode(o)(neo)

        val oo1 = Neo4jWrapper.deSerialize[Test](node)
        oo1 must beEqualTo(o)

        val oo2 = node.toCC[Test]
        oo2 must beEqualTo(Option(o))

        val oo3 = node.toCC[NotTest]
        oo3 must beEqualTo(None)

        Neo4jWrapper.deSerialize[NotTest](node) must throwA[IllegalArgumentException]
      }
    }

    "be serializable with Test2" in {
      withTx { neo =>
        val o = Test2(1, 3.3, true)
        val node = createNode(o)(neo)
        val oo1: Test2 = Neo4jWrapper.deSerialize[Test2](node)
        oo1 must beEqualTo(o)

        val oo2 = node.toCC[Test2]
        oo2 must beEqualTo(Option(o))

        val oo3 = node.toCC[NotTest]
        oo3 must beEqualTo(None)

        Neo4jWrapper.deSerialize[NotTest](node) must throwA[IllegalArgumentException]
      }
    }

    "be serializable with ArrayTest" in {
      withTx { implicit neo =>
        val o = ArrayTest(Array("foo", "bar", "baz", "qux"))
        val node = createNode(o)
        val oo1: ArrayTest = Neo4jWrapper.deSerialize[ArrayTest](node)
        val oo2 = node.toCC[ArrayTest]

        oo1.ar must beEqualTo(o.ar)
        oo2.get.ar must beEqualTo(o.ar)
      }
    }

    "be serializable with labels" in {
      withTx { neo =>
        val o = Test2(1, 3.3, true)
        val node = createNode(o, "A", "B")(neo)
        node.labels must beEqualTo(List("A", "B"))

        val oo2 = node.toCC[Test2]
        oo2 must beEqualTo(Option(o))
      }
    }

    "be possible with relations" in {
      val o = Test2(1, 3.3, true)
      withTx {
        implicit neo =>
          val start = createNode()
          val end = createNode()
          end <-- "foo" <-- start < o

          val rel = start.getSingleRelationship("foo", Direction.OUTGOING)
          val oo = rel.toCC[Test2]
          oo must beEqualTo(Some(o))
      }
    }

    "be possible to do polymorphic case classes" in {
      withTx { neo =>
        val n1 = createNode(Poly1("Poly1"))(neo)
        val n2 = createNode(Poly2("Poly2"))(neo)
        n1.toCC[PolyBase].get match {
          case p1: Poly1 => println(p1)
          case _ => failure
        }

        n2.toCC[PolyBase].get match {
          case p2: Poly2 => println(p2)
          case _ => failure
        }
      }
      success
    }
  }
}
