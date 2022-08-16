package util;

public class CenterControl {
    public static final boolean _FAST_REG_ALLOCATE = false;
    public static final boolean _IMM_TRY = false;
    public static boolean _ONLY_FRONTEND = false;
    public static final boolean _cutLiveNessShortest = false;
    public static final boolean _FixStackWithPeepHole = true;
    public static final boolean _AGGRESSIVE_ADD_MOD_OPT = false;
    public static final boolean _OPEN_PARALLEL = false;
    public static final boolean _GLOBAL_BSS = false;
    public static boolean AlreadyBackend = false;

    public static final String _TAG = "1";
    public static final boolean _initAll = false;


    //O2-control
    public static final boolean _OPEN_CONST_TRANS_FOLD = true;
    public static final boolean _OPEN_FLOAT_INSTR_COMB = true;
    public static final boolean _OPEN_FLOAT_LOOP_STRENGTH_REDUCTION = true;
    public static final boolean _OPEN_FTOI_ITOF_GVN = true;
}
