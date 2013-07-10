/*
Author: Danny Knowles
    Don Shea
*/

package gov.nasa.gsfc.seadas.processing.processor;

import com.bc.ceres.swing.selection.SelectionChangeEvent;
import com.bc.ceres.swing.selection.SelectionChangeListener;
import gov.nasa.gsfc.seadas.processing.core.MultiParamList;
import gov.nasa.gsfc.seadas.processing.core.ParamInfo;
import gov.nasa.gsfc.seadas.processing.core.ParamList;
import gov.nasa.gsfc.seadas.processing.core.ProcessorModel;
import gov.nasa.gsfc.seadas.processing.general.*;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.visat.VisatApp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;


public class SPForm extends JPanel implements CloProgramUI {

    public enum Processor {
        MAIN("multilevel_processor.py"),
        MODIS_L1A_PY("modis_L1A.py"),
        L1AEXTRACT_MODIS("l1aextract_modis"),
        L1AEXTRACT_SEAWIFS("l1aextract_seawifs"),
        L1MAPGEN("l1mapgen"),
        GEO("geo"),
        MODIS_L1B("modis_L1B.py"),
        L1BGEN("l1bgen"),
        L1BRSGEN("l1brsgen"),
        L2GEN("l2gen"),
        L2EXTRACT("l2extract"),
        L2BRSGEN("l2brsgen"),
        L2MAPGEN("l2mapgen"),
        L2BIN("l2bin"),
        L3BIN("l3bin"),
        SMIGEN("smigen");

        private Processor(String name) {
            this.name = name;
        }

        private final String name;

        public String toString() {
            return name;
        }
    }


    /*
   SPForm
       tabbedPane
           mainPanel
               primaryIOPanel
                   sourceProductFileSelector (ifile)
               parfilePanel
                   importPanel
                       importParfileButton
                       retainParfileCheckbox
                   exportParfileButton
                   parfileScrollPane
                       parfileTextArea
           chainScrollPane
               chainPanel
                   nameLabel
                   keepLabel
                   paramsLabel
                   configLabel
                   progRowPanel


    */

    private AppContext appContext;

    private JFileChooser jFileChooser;

    private final JTabbedPane tabbedPane;

    private JPanel mainPanel;
    private JPanel primaryIOPanel;
    private SourceProductFileSelector sourceProductFileSelector;
    private JScrollPane parfileScrollPane;
    private JPanel parfilePanel;
    private JPanel importPanel;
    private JButton importParfileButton;
    private JCheckBox retainIFileCheckbox;
    private JButton exportParfileButton;
    private JTextArea parfileTextArea;
    private FileSelector odirSelector;

    private JScrollPane chainScrollPane;
    private JPanel chainPanel;
    private JLabel nameLabel;
    private JLabel keepLabel;
    private JLabel paramsLabel;

    private JPanel spacer;

    private ArrayList<SPRow> rows;

    String xmlFileName;
    ProcessorModel processorModel;

    SPForm(AppContext appContext, String xmlFileName) {
        this.appContext = appContext;
        this.xmlFileName = xmlFileName;

        jFileChooser = new JFileChooser();

        // create main panel
        sourceProductFileSelector = new SourceProductFileSelector(VisatApp.getApp(), "ifile", true);
        sourceProductFileSelector.initProducts();
        //sourceProductFileSelector.setProductNameLabel(new JLabel("ifile"));
        sourceProductFileSelector.getProductNameComboBox().setPrototypeDisplayValue(
                "123456789 123456789 123456789 123456789 123456789 ");
        sourceProductFileSelector.addSelectionChangeListener(new SelectionChangeListener() {
            @Override
            public void selectionChanged(SelectionChangeEvent selectionChangeEvent) {
                handleIFileChanged();
            }

            @Override
            public void selectionContextChanged(SelectionChangeEvent selectionChangeEvent) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        });

        odirSelector = new FileSelector(VisatApp.getApp(), ParamInfo.Type.DIR, "odir");

        odirSelector.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
                handleOdirChanged();
            }
        });




        primaryIOPanel = new JPanel(new GridBagLayout());
        primaryIOPanel.setBorder(BorderFactory.createTitledBorder("Primary I/O Files"));
        primaryIOPanel.add(sourceProductFileSelector.createDefaultPanel(),
                new GridBagConstraintsCustom(0, 0, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL));

        primaryIOPanel.add(odirSelector.getjPanel(),
                new GridBagConstraintsCustom(0, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL));

        retainIFileCheckbox = new JCheckBox("Retain Selected IFILE");

        importParfileButton = new JButton("Import Parfile");
        importParfileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String contents = SeadasGuiUtils.importFile(jFileChooser);
                if (contents != null) {
                    setParamString(contents, retainIFileCheckbox.isSelected());
                }
            }
        });

        importPanel = new JPanel(new GridBagLayout());
        importPanel.setBorder(BorderFactory.createEtchedBorder());
        importPanel.add(importParfileButton,
                new GridBagConstraintsCustom(0, 0, 1, 1, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL));
        importPanel.add(retainIFileCheckbox,
                new GridBagConstraintsCustom(1, 0, 1, 1, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL));

        exportParfileButton = new JButton("Export Parfile");
        exportParfileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String contents = getParamString();
                SeadasGuiUtils.exportFile(jFileChooser, contents + "\n");
            }
        });

        parfileTextArea = new JTextArea();
        parfileTextArea.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public void focusLost(FocusEvent e) {
                handleParamStringChange();
            }
        });
        parfileScrollPane = new JScrollPane(parfileTextArea);
        parfileScrollPane.setBorder(null);

        parfilePanel = new JPanel(new GridBagLayout());
        parfilePanel.setBorder(BorderFactory.createTitledBorder("Parfile"));
        parfilePanel.add(importPanel,
                new GridBagConstraintsCustom(0, 0, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE));
        parfilePanel.add(exportParfileButton,
                new GridBagConstraintsCustom(1, 0, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE));
        parfilePanel.add(parfileScrollPane,
                new GridBagConstraintsCustom(0, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH, 0, 2));

        mainPanel = new JPanel(new GridBagLayout());
        mainPanel.add(primaryIOPanel,
                new GridBagConstraintsCustom(0, 0, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL));
        mainPanel.add(parfilePanel,
                new GridBagConstraintsCustom(0, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH));

        // create chain panel
        nameLabel = new JLabel("Processor");
        Font font = nameLabel.getFont().deriveFont(Font.BOLD);
        nameLabel.setFont(font);
        keepLabel = new JLabel("Keep");
        keepLabel.setToolTipText("Keep intermediate output files");
        keepLabel.setFont(font);
        paramsLabel = new JLabel("Parameters");
        paramsLabel.setFont(font);
        spacer = new JPanel();

        chainPanel = new JPanel(new GridBagLayout());

        chainPanel.add(nameLabel,
                new GridBagConstraintsCustom(0, 0, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(2, 2, 2, -8)));
        chainPanel.add(keepLabel,
                new GridBagConstraintsCustom(1, 0, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(2, -8, 2, -8)));
        chainPanel.add(paramsLabel,
                new GridBagConstraintsCustom(2, 0, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(2, -8, 2, 2)));
        createRows();
        int rowNum = 1;
        for (SPRow row : rows) {
            row.attachComponents(chainPanel, rowNum);
            rowNum++;
        }
        chainPanel.add(spacer,
                new GridBagConstraintsCustom(0, rowNum, 0, 1, GridBagConstraints.WEST, GridBagConstraints.VERTICAL));

        chainScrollPane = new JScrollPane(chainPanel);
        chainScrollPane.setBorder(null);

        tabbedPane = new JTabbedPane();
        tabbedPane.add("Main", mainPanel);
        tabbedPane.add("Processor Chain", chainScrollPane);

        // add the tabbed pane
        setLayout(new GridBagLayout());
        add(tabbedPane, new GridBagConstraintsCustom(0, 0, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH));

    }

    void createRows() {
        Processor[] rowNames = {
                Processor.MAIN,
                Processor.MODIS_L1A_PY,
                Processor.L1AEXTRACT_SEAWIFS,
                Processor.L1BRSGEN,
                Processor.L1MAPGEN,
                Processor.GEO,
                Processor.L1AEXTRACT_MODIS,
                Processor.MODIS_L1B,
                Processor.L1BGEN,
                Processor.L2GEN,
                Processor.L2EXTRACT,
                Processor.L2BIN,
                Processor.L2BRSGEN,
                Processor.L2MAPGEN,
                Processor.L3BIN,
                Processor.SMIGEN
        };
        rows = new ArrayList<SPRow>();

        for (Processor processor : rowNames) {
            SPRow row = new SPRow(processor.toString(), this);
            row.addPropertyChangeListener(SPRow.PARAM_STRING_EVENT, new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    updateParamString();
                }
            });
            rows.add(row);

        }
    }


    public AppContext getAppContext() {
        return appContext;
    }

    @Override
    public JPanel getParamPanel() {
        return this;
    }

    public ParamList getParamList() {
        MultiParamList paramList = new MultiParamList();
        for (SPRow row : rows) {
            String name = row.getName();
            if (name.equals("modis_GEO.py")) {
                name = "geo";
            }
            paramList.addParamList(name, row.getParamList());
        }
        return paramList;
    }

    @Override
    public ProcessorModel getProcessorModel() {
        if (processorModel == null) {
            processorModel = new ProcessorModel("multilevel_processor.py", xmlFileName);
            processorModel.setReadyToRun(true);
        }
        processorModel.setParamList(getParamList());
        return processorModel;
    }

    @Override
    public Product getSelectedSourceProduct() {
        if (getSourceProductFileSelector() != null) {
            return getSourceProductFileSelector().getSelectedProduct();
        }
        return null;
    }

    @Override
    public boolean isOpenOutputInApp() {
        return false;
    }

    public SourceProductFileSelector getSourceProductFileSelector() {
        return sourceProductFileSelector;
    }

    public void prepareShow() {
        if (getSourceProductFileSelector() != null) {
            getSourceProductFileSelector().initProducts();
        }
    }

    public void prepareHide() {
        if (getSourceProductFileSelector() != null) {
            getSourceProductFileSelector().releaseProducts();
        }
    }

    private SPRow getRow(String name) {
        for (SPRow row : rows) {
            if (row.getName().equals(name)) {
                return row;
            }
        }
        return null;
    }

    public String getParamString() {
        return getParamList().getParamString("\n");
    }

    public void setParamString(String str) {
        setParamString(str, false);
    }


    public void setParamString(String str, boolean retainIFile) {
        String[] lines = str.split("\n");

        String section = Processor.MAIN.toString();
        StringBuilder stringBuilder = new StringBuilder();

        for (String line : lines) {
            line = line.trim();

            // get rid of comment lines
            if (line.length() > 0 && line.charAt(0) != '#') {

                // locate new section line
                if (line.charAt(0) == '[' && line.contains("]")) {

                    // determine next section
                    int endIndex = line.indexOf(']');
                    String nextSection = line.substring(1, endIndex).trim();


                    if (nextSection.length() > 0) {

                        // set the params for this section
                        if (stringBuilder.length() > 0) {
                            SPRow row = getRow(section);
                            if (row != null) {
                                row.setParamString(stringBuilder.toString(), retainIFile);
                            }
                            stringBuilder.setLength(0);
                        }

                        section = nextSection;
                    }


//                    line = line.substring(1).trim();
//                    String[] words = line.split("\\s+", 2);
//                    section = words[0];
//                    int i = section.indexOf(']');
//                    if (i != -1) {
//                        section = section.substring(0, i).trim();
//                    }


                } else {
                    stringBuilder.append(line).append("\n");
                }
            }
        }

        if (stringBuilder.length() > 0) {
            SPRow row = getRow(section);
            if (row != null) {
                row.setParamString(stringBuilder.toString(), retainIFile);
            }
        }

        updateParamString();
    }

    private void updateParamString() {
        sourceProductFileSelector.setSelectedFile(new File(getRow(Processor.MAIN.toString()).getParamList().getValue("ifile")));
        odirSelector.setFilename(getRow(Processor.MAIN.toString()).getParamList().getValue("odir"));
        parfileTextArea.setText(getParamString());
    }

    private void handleParamStringChange() {
        String newStr = parfileTextArea.getText();
        String oldStr = getParamString();
        if (!newStr.equals(oldStr)) {
            setParamString(newStr);
        }
    }

    private void handleIFileChanged() {
        String ifileName = sourceProductFileSelector.getSelectedProduct().getFileLocation().getAbsolutePath();
        SPRow row = getRow(Processor.MAIN.toString());
        String oldIFile = row.getParamList().getValue("ifile");
        if (!ifileName.equals(oldIFile)) {

//            getRow("l2gen").clearConfigPanel();

            row.setParamValue("ifile", ifileName);
            parfileTextArea.setText(getParamString());
        }
    }


    private void handleOdirChanged() {
        String odirName = odirSelector.getFileName();
        SPRow row = getRow(Processor.MAIN.toString());
        String oldOdir = row.getParamList().getValue("odir");
        if (!odirName.equals(oldOdir)) {
            row.setParamValue("odir", odirName);
            parfileTextArea.setText(getParamString());
        }
    }



    public String getIFile() {
        return getRow(Processor.MAIN.toString()).getParamList().getValue("ifile");
    }

    public String getFirstIFile() {
        String fileName = getIFile();
        if (fileName.contains(",")) {
            String[] files = fileName.split(",");
            fileName = files[0].trim();
        } else if (fileName.contains(" ")) {
            String[] files = fileName.trim().split(" ");
            fileName = files[0].trim();
        }

        // todo : need to check for file being a list of files.

        return fileName;
    }


}