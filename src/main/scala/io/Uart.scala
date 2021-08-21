//package io
//
//import chisel3._
//import chisel3.util._
//
//class UartIO extends Bundle{
//  val TxD_uart_start = Output(Bool())
//  val TxD_uart_data = Output(UInt(8.W))
//  val TxD_uart_busy = Input(Bool())
//
//  val Rxd_uart_ready = Input(Bool())
//  val Rxd_uart_clear = Output(Bool())
//  val RxD_uart_data = Input(UInt(8.W))
//
//  val dcache_read_data = Output(UInt(8.W))
//  val dcache_write_uart = Input(Bool())
//  val dcache_write_uart_data = Input(UInt(8.W))
//}
//
//class Uart extends Module {
//  val io =IO(new UartIO)
//  val dcache_uart_data = RegInit(0.U(8.W))
//  val dcache_uart_valid = RegInit(false.B)
//  val TxD_uart_start = RegInit(false.B)
//  val TxD_uart_data = RegInit(0.U(8.W))
//  io.dcache_read_data:=dcache_uart_data
//  io.TxD_uart_start:=TxD_uart_start
//  io.TxD_uart_data:=TxD_uart_data
//
//  when(io.Rxd_uart_ready){
//    dcache_uart_data:=io.RxD_uart_data
//    dcache_uart_valid:=true.B
//    io.Rxd_uart_clear:=true.B
//  }.otherwise{
//    io.Rxd_uart_clear:=false.B
//  }
//
//  when(io.dcache_write_uart&& !io.TxD_uart_busy){
//    TxD_uart_start:=true.B
//    TxD_uart_data:=io.dcache_write_uart_data
//  }.otherwise{
//    TxD_uart_start:=false.B
//  }
//
//
//
//}
