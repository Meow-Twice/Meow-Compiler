package lir;

import java.io.PrintStream;

public class BJ extends MachineInst {
    public BJ(Tag tag, MC.Block insertAtEnd) {
        super(tag, insertAtEnd);
    }

    public BJ(Tag tag, MachineInst before) {
        super(tag, before);
    }

    public MC.Block getTrueBlock() {
        return null;
    }

    public MC.Block getFalseBlock() {
        return null;
    }

    public void setTarget(MC.Block onlySuccMB) {
        assert false;
    }


    public static class GDJump extends BJ {
        public MC.Block getTarget() {
            return target;
        }

        MC.Block target;

        public GDJump(MC.Block target, MC.Block insertAtEnd) {
            super(Tag.Jump, insertAtEnd);
            this.target = target;
        }

        public GDJump(Arm.Cond cond, MC.Block target, MachineInst before) {
            super(Tag.Jump, before);
            this.target = target;
            this.cond = cond;
        }

        public GDJump(Arm.Cond cond, MC.Block target, MC.Block insertAtEnd) {
            super(Tag.Jump, insertAtEnd);
            this.target = target;
            this.cond = cond;
        }

        public MC.Block getTrueBlock() {
            return target;
        }

        public MC.Block getFalseBlock() {
            if (!(mb.getNext() instanceof MC.Block))
                System.exit(123);
            return (MC.Block) this.mb.getNext();
        }

        @Override
        public void output(PrintStream os, MC.McFunction f) {
            os.println("\t" + this);
        }

        @Override
        public String toString() {
            return tag.toString() + cond + "\t" + target.getLabel();
        }

        public void setTarget(MC.Block block) {
            target = block;
        }


    }


    public static class GDBranch extends BJ {
        public Arm.Cond getCond() {
            return cond;
        }

        public MC.Block getTrueTargetBlock() {
            return trueTargetBlock;
        }

        public MC.Block getFalseTargetBlock() {
            return falseTargetBlock;
        }

        Arm.Cond cond;
        MC.Block trueTargetBlock;
        // 条件不满足则跳这个块
        MC.Block falseTargetBlock;

        public GDBranch(Arm.Cond cond, MC.Block trueBlock, MC.Block falseBlock, MC.Block insertAtEnd) {
            super(Tag.Branch, insertAtEnd);
            this.cond = cond;
            trueTargetBlock = trueBlock;
            falseTargetBlock = falseBlock;
        }

        @Override
        public String toString() {
            return tag.toString() + cond + "\t" + trueTargetBlock.getLabel() + "\n" +
                    "\t" + tag.toString() + "\t" + falseTargetBlock.getLabel();
        }


        public MC.Block getTrueBlock() {
            return trueTargetBlock;
        }

        public MC.Block getFalseBlock() {
            return falseTargetBlock;
        }

        public void setTarget(MC.Block onlySuccMB) {
            trueTargetBlock = onlySuccMB;
        }

    }

}
