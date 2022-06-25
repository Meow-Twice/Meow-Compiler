package ir.type;

public enum DataType{
    INT("i32"), FLOAT("f32"), BOOL("i1");

    private String dataTypeName;

    DataType(String dataTypeName) {
        this.dataTypeName = dataTypeName;
    }

    @Override
    public String toString() {
        return dataTypeName;
    }

    public String getDataTypeName() {
        return dataTypeName;
    }
}
