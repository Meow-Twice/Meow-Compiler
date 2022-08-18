package util;

public class CenterControl {
    public static final boolean _FAST_REG_ALLOCATE = false;
    public static final boolean _IMM_TRY = false;
    public static final boolean STABLE = true;
    public static boolean _ONLY_FRONTEND = false;
    public static final boolean _cutLiveNessShortest = false;
    public static final boolean _FixStackWithPeepHole = true;
    public static final boolean _AGGRESSIVE_ADD_MOD_OPT = false;
    public static final boolean _OPEN_PARALLEL = true;
    public static final boolean _GLOBAL_BSS = true;
    public static boolean AlreadyBackend = false;

    public static final String _TAG = "1";
    public static final boolean _initAll = false;


    //O2-control
    public static final boolean _CLOSE_ALL_FLOAT_OPT = true;
    public static final boolean _OPEN_CONST_TRANS_FOLD = _CLOSE_ALL_FLOAT_OPT? false : true;
    public static final boolean _OPEN_FLOAT_INSTR_COMB = _CLOSE_ALL_FLOAT_OPT? false : true;
    public static final boolean _OPEN_FLOAT_LOOP_STRENGTH_REDUCTION = _CLOSE_ALL_FLOAT_OPT? false : true;
    public static final boolean _OPEN_FTOI_ITOF_GVN = _CLOSE_ALL_FLOAT_OPT? false : true;


    public static final boolean _STRONG_FUNC_INLINE = true;
}
