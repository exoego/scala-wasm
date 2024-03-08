package testsuite.core.hijackedclassesupcast

import scala.scalajs.js.annotation._

object HijackedClassesUpcastTest {
  def main(): Unit = { val _ = test() }

  @JSExportTopLevel("hijackedClassesUpcast")
  def test(): Boolean = {
    testInteger(5) &&
      testIntegerNull(null) &&
      testString("foo") &&
      testStringNull(null)
  }

  def testInteger(x: Int): Boolean = {
    val x1 = identity(x)
    x1 == 5 && {
      val x2: Any = x1
      x2 match {
        case x3: Int => x3 + 1 == 6
        case _       => false
      }
    }
  }

  def testIntegerNull(x: Any): Boolean = {
    !x.isInstanceOf[Int] &&
      !x.isInstanceOf[java.lang.Integer] &&
      (x.asInstanceOf[Int] == 0) && {
        val x2 = x.asInstanceOf[java.lang.Integer]
        true // TODO test that it is null
      }
  }

  def testString(x: String): Boolean = {
    val x1 = identity(x)
    x1.length() == 3 && {
      val x2: Any = x1
      x2 match {
        case x3: String => x3.length() == 3
        case _          => false
      }
    }
  }

  def testStringNull(x: Any): Boolean = {
    !x.isInstanceOf[String] && {
      val x2 = x.asInstanceOf[String]
      true // TODO test that it is null
    }
  }

  @noinline
  def identity[A](x: A): A = x
}
