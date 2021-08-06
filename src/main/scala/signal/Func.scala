package signal

import chisel3.experimental.ChiselEnum

object uOP extends ChiselEnum {
  val
  NOP,
  //alu
  Alu_ADD,
  Alu_ADDU,
  Alu_SUB,
  Alu_SUBU,
  Alu_SLL,
  Alu_SRL,
  Alu_SRA,
  Alu_SLT,
  Alu_SLTU,
  Alu_XOR,
  Alu_OR,
  Alu_AND,
  Alu_LUI,
  Alu_NOR,
  //mdu
  Mdu_MUL,
  Mdu_MULT,
  Mdu_MULTU,
  Mdu_DIV,
  Mdu_DIVU,
  Mdu_MFHI,
  Mdu_MFLO,
  Mdu_MTHI,
  Mdu_MTLO,
  //bju
  Bju_J,
  Bju_JAL,
  Bju_JR,
  Bju_JALR,
  Bju_BEQ,
  Bju_BNE,
  Bju_BLEZ,
  Bju_BGTZ,
  Bju_BLTZ,
  Bju_BGEZ,
  Bju_BGEZAL,
  Bju_BLTZAL,
  //mem
  Mm_SW,
  Mm_SH,
  Mm_SB,
  Mm_LW,
  Mm_LH,
  Mm_LHU,
  Mm_LB,
  Mm_LBU,
  //cp0
  Cp0_MT,
  Cp0_MF,
  SYSCALL,
  ERET,
  BREAK = Value
}


object UnitSel extends ChiselEnum {
  val
  is_Null,
  is_Alu,
  is_Cp0,
  is_Mem,
  is_Mdu,
  is_Bju = Value
}

object TargetSel extends ChiselEnum {
  val
  none,
  mem,
  reg,
  cp0,
  hilo,
  j,
  jal = Value
}

object ImmSel extends ChiselEnum {
  val
  zero_ex,
  sign_ex,
  is_sa,
  is_instr_index,
  no_imm = Value
}

object OpSel extends ChiselEnum {
  val
  is_rs,
  is_rt,
  is_rd,
  is_ra,
  is_null = Value
}

object ExceptSel extends ChiselEnum {
  val
  is_null,
  is_syscall,
  is_break = Value
}

