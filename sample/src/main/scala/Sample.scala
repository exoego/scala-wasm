package sample

import scala.annotation.tailrec

import scala.scalajs.js
import scala.scalajs.js.annotation._

object Main {
  @JSExportTopLevel("test")
  def test(i: Int): Boolean = {
    val loopFib = fib(new LoopFib {}, i)
    val recFib = fib(new RecFib {}, i)
    val tailrecFib = fib(new TailRecFib {}, i)
    js.Dynamic.global.console
      .log(s"loopFib: $loopFib -- recFib: $recFib -- tailrecFib: $tailrecFib")
    val date = new js.Date(0)
    js.Dynamic.global.console.log(date)
    loopFib == recFib && loopFib == tailrecFib
  }
  def fib(fib: Fib, n: Int): Int = fib.fib(n)
}

trait LoopFib extends Fib {
  def fib(n: Int): Int = {
    var a = 0
    var b = 1
    var i = 0
    while (i < n) {
      val temp = b
      b = a + b
      a = temp
      i += 1
    }
    a
  }

}

trait RecFib extends Fib {
  def fib(n: Int): Int =
    if (n <= 1) {
      n
    } else {
      fib(n - 1) + fib(n - 2)
    }
}

trait TailRecFib extends Fib {
  def fib(n: Int): Int = fibLoop(n, 0, 1)

  @tailrec
  final def fibLoop(n: Int, a: Int, b: Int): Int =
    if (n == 0) a
    else fibLoop(n - 1, b, a + b)
}

trait Fib {
  def fib(n: Int): Int
}
