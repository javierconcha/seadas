package gov.nasa.gsfc.seadas.contour.ui;

import java.awt.*;
import java.text.DecimalFormat;

/**
 * Created by IntelliJ IDEA.
 * User: Aynur Abdurazik (aabduraz)
 * Date: 5/27/14
 * Time: 6:17 PM
 * To change this template use File | Settings | File Templates.
 */
public class ContourInterval {
    String contourLevelName;
    Double contourLevelValue;
    private Color lineColor;
    DecimalFormat decimalFormat = new DecimalFormat("##.###");

    ContourInterval(String contourBaseName, Double contourLevelValue) {
        this.contourLevelValue = new Double(decimalFormat.format(contourLevelValue));
        contourLevelName = contourBaseName + this.contourLevelValue;
        lineColor = Color.WHITE;
    }

    ContourInterval(Double contourLevelValue) {
        this.contourLevelValue = new Double(decimalFormat.format(contourLevelValue));
        lineColor = Color.WHITE;
    }

    ContourInterval() {
    }

    public void setContourLevelName(String contourLevelName) {
        this.contourLevelName = contourLevelName;
    }

    public String getContourLevelName() {
        return contourLevelName;
    }

    public void setContourLevelValue(Double contourLevelValue) {
        this.contourLevelValue = new Double(decimalFormat.format(contourLevelValue));
    }

    public double getContourLevelValue() {
        return contourLevelValue;
    }

    public Color getLineColor() {
        return lineColor;
    }

    public void setLineColor(Color lineColor) {
        this.lineColor = lineColor;
    }

    @Override
    public ContourInterval clone(){
        ContourInterval contourInterval = new ContourInterval();
        contourInterval.setLineColor(new Color(this.getLineColor().getRed(),
                                               this.getLineColor().getGreen(),
                                               this.getLineColor().getBlue(),
                                               this.getLineColor().getAlpha()));
        contourInterval.setContourLevelName(this.getContourLevelName());
        contourInterval.setContourLevelValue(this.getContourLevelValue());
        return contourInterval;

    }
}