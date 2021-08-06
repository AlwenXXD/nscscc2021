//package io
//
//import chisel3._
//import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
//import chiseltest._
//import chiseltest.experimental.TestOptionBuilder.ChiselScalatestOptionBuilder
//import chiseltest.internal.WriteVcdAnnotation
//import cpu.ifu.BPU
//import org.scalatest.FlatSpec
//
//class SramTest extends FlatSpec with ChiselScalatestTester {
//  (new ChiselStage).execute(Array.empty, Seq(ChiselGeneratorAnnotation(() => new Sram)))
//  "Sram" should "read" in {
//    test(new Sram).withAnnotations(Seq(WriteVcdAnnotation)){
//      i=>
//        i.io.ram_rd.req.valid.poke(true.B)
//        i.io.ram_rd.req.bits.addr.poke(1.U(32.W))
//        i.io.ram_ctrl.data_in.poke("hffff_ffff".U(32.W))
//        i.io.ram_wr.req.valid.poke(false.B)
//        i.io.ram_wr.req.bits.addr.poke(1.U(32.W))
//        i.io.ram_wr.req.bits.data.poke("hffff_ffff".U(32.W))
//        i.clock.step()
//
//    }
//  }
//}
//
