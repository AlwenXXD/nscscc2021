package instructions


import chisel3.{Bool, fromBooleanToLiteral}
import chisel3.util.BitPat
import signal._

object MIPS32 {
  //arithmetic
  val ADD     = BitPat("b000000???????????????00000100000")
  val ADDI    = BitPat("b001000??????????????????????????")
  val ADDU    = BitPat("b000000???????????????00000100001")
  val ADDIU   = BitPat("b001001??????????????????????????")
  val SUB     = BitPat("b000000???????????????00000100010")
  val SUBU    = BitPat("b000000???????????????00000100011")
  val SLT     = BitPat("b000000???????????????00000101010")
  val SLTI    = BitPat("b001010??????????????????????????")
  val SLTU    = BitPat("b000000???????????????00000101011")
  val SLTIU   = BitPat("b001011??????????????????????????")
  //division & multiply
  val MUL     = BitPat("b011100???????????????00000000010")
  val DIV     = BitPat("b000000??????????0000000000011010")
  val DIVU    = BitPat("b000000??????????0000000000011011")
  val MULT    = BitPat("b000000??????????0000000000011000")
  val MULTU   = BitPat("b000000??????????0000000000011001")
  val MFHI    = BitPat("b0000000000000000?????00000010000")
  val MFLO    = BitPat("b0000000000000000?????00000010010")
  val MTHI    = BitPat("b000000?????000000000000000010001")
  val MTLO    = BitPat("b000000?????000000000000000010011")
  //logic
  val AND     = BitPat("b000000???????????????00000100100")
  val ANDI    = BitPat("b001100??????????????????????????")
  val LUI     = BitPat("b00111100000?????????????????????")
  val NOR     = BitPat("b000000???????????????00000100111")
  val OR      = BitPat("b000000???????????????00000100101")
  val ORI     = BitPat("b001101??????????????????????????")
  val XOR     = BitPat("b000000???????????????00000100110")
  val XORI    = BitPat("b001110??????????????????????????")
  //shift
  val SLLV    = BitPat("b000000???????????????00000000100")
  val SLL     = BitPat("b00000000000???????????????000000")
  val SRAV    = BitPat("b000000???????????????00000000111")
  val SRA     = BitPat("b00000000000???????????????000011")
  val SRLV    = BitPat("b000000???????????????00000000110")
  val SRL     = BitPat("b00000000000???????????????000010")
  //branch & jump
  val BEQ     = BitPat("b000100??????????????????????????")
  val BNE     = BitPat("b000101??????????????????????????")
  val BGEZ    = BitPat("b000001?????00001????????????????")
  val BGTZ    = BitPat("b000111?????00000????????????????")
  val BLEZ    = BitPat("b000110?????00000????????????????")
  val BLTZ    = BitPat("b000001?????00000????????????????")
  val BGEZAL  = BitPat("b000001?????00001????????????????")
  val BLTZAL  = BitPat("b000110?????00000????????????????")
  val J       = BitPat("b000010??????????????????????????")
  val JAL     = BitPat("b000011??????????????????????????")
  val JR      = BitPat("b000000?????000000000000000001000")
  val JALR    = BitPat("b000000?????00000?????00000001001")
  //cp0
  val MFC0    = BitPat("b01000000000??????????00000000000")
  val MTC0    = BitPat("b01000000100??????????00000000000")
  val SYSCALL = BitPat("b00000000000000000000000000001100")
  val BREAK   = BitPat("b000000????????????????????001101")
  val ERET    = BitPat("b01000010000000000000000000011000")
  // load & store
  val LB      = BitPat("b100000??????????????????????????")
  val LBU     = BitPat("b100100??????????????????????????")
  val LH      = BitPat("b100001??????????????????????????")
  val LHU     = BitPat("b100101??????????????????????????")
  val LW      = BitPat("b100011??????????????????????????")
  val SB      = BitPat("b101000??????????????????????????")
  val SH      = BitPat("b101001??????????????????????????")
  val SW      = BitPat("b101011??????????????????????????")

  //                              uop unit_sel imm_sel op1_sel op2_sel des_sel flush_on_commit target_sel
  val decode_default = List(uOP.NOP, UnitSel.is_Null, ImmSel.no_imm, OpSel.is_null, OpSel.is_null, OpSel.is_null,false.B,TargetSel.none)
  val decode_table   = Array(
    ADD -> List(uOP.Alu_ADD, UnitSel.is_Alu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_rd,false.B,TargetSel.reg),
    ADDI -> List(uOP.Alu_ADD, UnitSel.is_Alu, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_rt,false.B,TargetSel.reg),
    ADDU -> List(uOP.Alu_ADDU, UnitSel.is_Alu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_rd,false.B,TargetSel.reg),
    ADDIU -> List(uOP.Alu_ADDU, UnitSel.is_Alu, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_rt,false.B,TargetSel.reg),
    SUB -> List(uOP.Alu_SUB, UnitSel.is_Alu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_rd,false.B,TargetSel.reg),
    SUBU -> List(uOP.Alu_SUBU, UnitSel.is_Alu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_rd,false.B,TargetSel.reg),
    SLT -> List(uOP.Alu_SLT, UnitSel.is_Alu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_rd,false.B,TargetSel.reg),
    SLTI -> List(uOP.Alu_SLT, UnitSel.is_Alu, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_rt,false.B,TargetSel.reg),
    SLTU -> List(uOP.Alu_SLTU, UnitSel.is_Alu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_rd,false.B,TargetSel.reg),
    SLTIU -> List(uOP.Alu_SLTU, UnitSel.is_Alu, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_rt,false.B,TargetSel.reg),

    MUL -> List(uOP.Mdu_MUL, UnitSel.is_Mdu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_rd,false.B,TargetSel.reg),
    DIV -> List(uOP.Mdu_DIV, UnitSel.is_Mdu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_null,false.B,TargetSel.hilo),
    DIVU -> List(uOP.Mdu_DIVU, UnitSel.is_Mdu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_null,false.B,TargetSel.hilo),
    MULT -> List(uOP.Mdu_MULT, UnitSel.is_Mdu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_null,false.B,TargetSel.hilo),
    MULTU -> List(uOP.Mdu_MULTU, UnitSel.is_Mdu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_null,false.B,TargetSel.hilo),
    MFHI -> List(uOP.Mdu_MFHI, UnitSel.is_Mdu, ImmSel.no_imm, OpSel.is_null, OpSel.is_null, OpSel.is_rd,false.B,TargetSel.reg),
    MFLO -> List(uOP.Mdu_MFLO, UnitSel.is_Mdu, ImmSel.no_imm, OpSel.is_null, OpSel.is_null, OpSel.is_rd,false.B,TargetSel.reg),
    MTHI -> List(uOP.Mdu_MTHI, UnitSel.is_Mdu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_null, OpSel.is_null,false.B,TargetSel.hilo),
    MTLO -> List(uOP.Mdu_MTLO, UnitSel.is_Mdu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_null, OpSel.is_null,false.B,TargetSel.hilo),

    AND -> List(uOP.Alu_AND, UnitSel.is_Alu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_rd,false.B,TargetSel.reg),
    ANDI -> List(uOP.Alu_AND, UnitSel.is_Alu, ImmSel.zero_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_rt,false.B,TargetSel.reg),
    LUI -> List(uOP.Alu_LUI, UnitSel.is_Alu, ImmSel.zero_ex, OpSel.is_null, OpSel.is_null, OpSel.is_rt,false.B,TargetSel.reg),
    NOR -> List(uOP.Alu_NOR, UnitSel.is_Alu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_rd,false.B,TargetSel.reg),
    OR -> List(uOP.Alu_OR, UnitSel.is_Alu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_rd,false.B,TargetSel.reg),
    ORI -> List(uOP.Alu_OR, UnitSel.is_Alu, ImmSel.zero_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_rt,false.B,TargetSel.reg),
    XOR -> List(uOP.Alu_XOR, UnitSel.is_Alu, ImmSel.no_imm, OpSel.is_rs, OpSel.is_rt, OpSel.is_rd,false.B,TargetSel.reg),
    XORI -> List(uOP.Alu_XOR, UnitSel.is_Alu, ImmSel.zero_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_rt,false.B,TargetSel.reg),

    SLLV -> List(uOP.Alu_SLL, UnitSel.is_Alu, ImmSel.no_imm, OpSel.is_rt, OpSel.is_rs, OpSel.is_rd,false.B,TargetSel.reg),
    SLL -> List(uOP.Alu_SLL, UnitSel.is_Alu, ImmSel.is_sa, OpSel.is_rt, OpSel.is_null, OpSel.is_rd,false.B,TargetSel.reg),
    SRAV -> List(uOP.Alu_SRA, UnitSel.is_Alu, ImmSel.no_imm, OpSel.is_rt, OpSel.is_rs, OpSel.is_rd,false.B,TargetSel.reg),
    SRA -> List(uOP.Alu_SRA, UnitSel.is_Alu, ImmSel.is_sa, OpSel.is_rt, OpSel.is_null, OpSel.is_rd,false.B,TargetSel.reg),
    SRLV -> List(uOP.Alu_SRL, UnitSel.is_Alu, ImmSel.no_imm, OpSel.is_rt, OpSel.is_rs, OpSel.is_rd,false.B,TargetSel.reg),
    SRL -> List(uOP.Alu_SRL, UnitSel.is_Alu, ImmSel.is_sa, OpSel.is_rt, OpSel.is_null, OpSel.is_rd,false.B,TargetSel.reg),

    BEQ -> List(uOP.Bju_BEQ, UnitSel.is_Bju, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_rt, OpSel.is_null,false.B,TargetSel.j),
    BNE -> List(uOP.Bju_BNE, UnitSel.is_Bju, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_rt, OpSel.is_null,false.B,TargetSel.j),
    BGEZ -> List(uOP.Bju_BGEZ, UnitSel.is_Bju, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_null,false.B,TargetSel.j),
    BGTZ -> List(uOP.Bju_BGTZ, UnitSel.is_Bju, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_null,false.B,TargetSel.j),
    BLEZ -> List(uOP.Bju_BLEZ, UnitSel.is_Bju, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_null,false.B,TargetSel.j),
    BLTZ -> List(uOP.Bju_BLTZ, UnitSel.is_Bju, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_null,false.B,TargetSel.j),
    BGEZAL -> List(uOP.Bju_BGEZAL, UnitSel.is_Bju, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_ra,false.B,TargetSel.jal),
    BLTZAL -> List(uOP.Bju_BLTZAL, UnitSel.is_Bju, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_ra,false.B,TargetSel.jal),
    J -> List(uOP.Bju_J, UnitSel.is_Bju, ImmSel.is_instr_index, OpSel.is_null, OpSel.is_null, OpSel.is_null,true.B,TargetSel.j),
    JAL -> List(uOP.Bju_JAL, UnitSel.is_Bju, ImmSel.is_instr_index, OpSel.is_null, OpSel.is_null, OpSel.is_ra,true.B,TargetSel.jal),
    JR -> List(uOP.Bju_JR, UnitSel.is_Bju, ImmSel.no_imm, OpSel.is_rs, OpSel.is_null, OpSel.is_null,true.B,TargetSel.j),
    JALR -> List(uOP.Bju_JALR, UnitSel.is_Bju, ImmSel.no_imm, OpSel.is_rs, OpSel.is_null, OpSel.is_rd,true.B,TargetSel.jal),

    SYSCALL -> List(uOP.SYSCALL, UnitSel.is_Null, ImmSel.no_imm, OpSel.is_null, OpSel.is_null, OpSel.is_null,true.B,TargetSel.none),
    BREAK -> List(uOP.BREAK, UnitSel.is_Null, ImmSel.no_imm, OpSel.is_null, OpSel.is_null, OpSel.is_null,true.B,TargetSel.none),
    ERET -> List(uOP.ERET, UnitSel.is_Null, ImmSel.no_imm, OpSel.is_null, OpSel.is_null, OpSel.is_null,true.B,TargetSel.cp0),
    MFC0 -> List(uOP.Cp0_MF, UnitSel.is_Cp0, ImmSel.zero_ex, OpSel.is_rd, OpSel.is_null, OpSel.is_rt,false.B,TargetSel.cp0),
    MTC0 -> List(uOP.Cp0_MT, UnitSel.is_Cp0, ImmSel.zero_ex, OpSel.is_rt, OpSel.is_rd, OpSel.is_null,false.B,TargetSel.cp0),

    LB -> List(uOP.Mm_LB, UnitSel.is_Mem, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_rt,false.B,TargetSel.reg),
    LH -> List(uOP.Mm_LH, UnitSel.is_Mem, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_rt,false.B,TargetSel.reg),
    LW -> List(uOP.Mm_LW, UnitSel.is_Mem, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_rt,false.B,TargetSel.reg),
    LBU -> List(uOP.Mm_LBU, UnitSel.is_Mem, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_rt,false.B,TargetSel.reg),
    LHU -> List(uOP.Mm_LHU, UnitSel.is_Mem, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_null, OpSel.is_rt,false.B,TargetSel.reg),
    SB -> List(uOP.Mm_SB, UnitSel.is_Mem, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_rt, OpSel.is_null,false.B,TargetSel.mem),
    SH -> List(uOP.Mm_SH, UnitSel.is_Mem, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_rt, OpSel.is_null,false.B,TargetSel.mem),
    SW -> List(uOP.Mm_SW, UnitSel.is_Mem, ImmSel.sign_ex, OpSel.is_rs, OpSel.is_rt, OpSel.is_null,false.B,TargetSel.mem),
    )


}
