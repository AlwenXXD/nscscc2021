//package cpu.ifu
//
//import chisel3._
//import chiseltest._
//import chiseltest.experimental.TestOptionBuilder._
//import chiseltest.internal.WriteVcdAnnotation
//import org.scalatest.FlatSpec
//
//class ICacheTest extends FlatSpec with ChiselScalatestTester {
//  "Icache" should "get right instruction" in{
//    test(new ICache).withAnnotations(Seq(WriteVcdAnnotation)){
//      i=>
//
//        i.clock.step(2)
//        i.clock.step(2)
//    }
//  }
//}
