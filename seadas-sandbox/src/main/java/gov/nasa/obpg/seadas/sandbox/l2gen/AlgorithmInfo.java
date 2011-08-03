package gov.nasa.obpg.seadas.sandbox.l2gen;

/**
 * A ...
 *
 * @author Danny Knowles
 * @since SeaDAS 7.0
 */
public class AlgorithmInfo {

    private static String PARAMTYPE_VISIBLE = "VISIBLE";
    private static String PARAMTYPE_IR = "IR";
    private static String PARAMTYPE_ALL = "ALL";
    private static String PARAMTYPE_NONE = "NONE";

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public boolean isToStringShowProductName() {
        return toStringShowProductName;
    }

    public void setToStringShowProductName(boolean toStringShowProductName) {
        this.toStringShowProductName = toStringShowProductName;
    }

    public boolean isToStringShowParameterType() {
        return toStringShowParameterType;
    }

    public void setToStringShowParameterType(boolean toStringShowParameterType) {
        this.toStringShowParameterType = toStringShowParameterType;
    }


    public static enum ParameterType {
        VISIBLE, IR, ALL, NONE
    }

    private String name = null;
    private String productName = null;
    private boolean toStringShowProductName = false;
    private boolean toStringShowParameterType = false;
    private String description = null;
    private String dataType = null;
    private String prefix = null;
    private String suffix = null;
    private String units = null;
    private ParameterType parameterType = null;


    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ParameterType getParameterType() {
        return parameterType;
    }

    public void setParameterType(String parameterTypeStr) {
        this.parameterType = convertWavetype(parameterTypeStr);
    }



    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }



    public AlgorithmInfo(String name, String description, ParameterType parameterType) {
        this.name = name;
        this.description = description;
        this.parameterType = parameterType;
    }

    public AlgorithmInfo(String name, String description) {
        this(name, description, ParameterType.NONE);
    }

    public AlgorithmInfo() {
    }

    public AlgorithmInfo(String name, String description, String waveTypeStr) {
        this(name, description, convertWavetype(waveTypeStr));
    }


    private static ParameterType convertWavetype(String str) {
        if (str.compareToIgnoreCase(PARAMTYPE_VISIBLE) == 0) {
            return ParameterType.VISIBLE;
        } else if (str.compareToIgnoreCase(PARAMTYPE_IR) == 0) {
            return ParameterType.IR;
        } else if (str.compareToIgnoreCase(PARAMTYPE_ALL) == 0) {
            return ParameterType.ALL;
        } else if (str.compareToIgnoreCase(PARAMTYPE_NONE) == 0) {
            return ParameterType.NONE;
        } else {
            return null;
        }
    }

        public void dump() {
        System.out.println("  " + name);
        }

        public String toString() {

            StringBuilder myStringBuilder = new StringBuilder("");

            if (toStringShowProductName == true) {
                myStringBuilder.append(productName);
                myStringBuilder.append(':');
            }

            myStringBuilder.append(name);

            if (toStringShowParameterType == true) {
                myStringBuilder.append(':');
                myStringBuilder.append(parameterType);
            }


            return myStringBuilder.toString();
        }

    public int compareTo(AlgorithmInfo p) {
        return name.compareToIgnoreCase(p.name);
    }

}
