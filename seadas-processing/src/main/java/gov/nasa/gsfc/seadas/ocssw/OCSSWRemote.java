package gov.nasa.gsfc.seadas.ocssw;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.runtime.RuntimeContext;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import gov.nasa.gsfc.seadas.processing.common.SeadasFileUtils;
import gov.nasa.gsfc.seadas.processing.common.SeadasLogger;
import gov.nasa.gsfc.seadas.processing.common.SeadasProcess;
import gov.nasa.gsfc.seadas.processing.core.*;
import gov.nasa.gsfc.seadas.processing.l2gen.userInterface.L2genForm;
import org.esa.beam.visat.VisatApp;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;

import javax.json.*;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by aabduraz on 3/27/17.
 */
public class OCSSWRemote extends OCSSW {

    public static final String OCSSW_SERVER_PORT_NUMBER = "6400";
    public static final String SEADAS_CLIENT_ID_PROPERTY = "client.id";
    public static final String MLP_PROGRAM_NAME = "multilevel_processor.py";
    public static final String MLP_PAR_FILE_ODIR_KEY_NAME = "odir";


    public static final String PROCESS_STATUS_NONEXIST = "-100";
    public static final String PROCESS_STATUS_STARTED = "-1";
    public static final String PROCESS_STATUS_COMPLETED = "0";
    public static final String PROCESS_STATUS_FAILED = "1";

    public static final String PROGRAMS_NEED_ADDITIONAL_FILES = "l2gen,l3gen,l2gen_aquarius";

    public static final String ADDITIONAL_FILE_EXTENSIONS = "L1B_HKM, L1B_QKM, L1B_LAC.anc";

    OCSSWClient ocsswClient;
    WebTarget target;

    String jobId;
    String clientId;
    boolean ifileUploadSuccess;
    String ofileName;
    String ofileDir;
    String ifileDir;

    ProcessorModel processorModel;


    public OCSSWRemote() {
        initialize();
    }

    private void initialize() {
        ocsswClient = new OCSSWClient(ocsswInfo.getResourceBaseUri());
        target = ocsswClient.getOcsswWebTarget();
        if (ocsswInfo.isOcsswExist()) {
            jobId = target.path("jobs").path("newJobId").request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
            clientId = RuntimeContext.getConfig().getContextProperty(SEADAS_CLIENT_ID_PROPERTY, System.getProperty("user.home"));
            target.path("ocssw").path("ocsswSetClientId").path(jobId).request().put(Entity.entity(clientId, MediaType.TEXT_PLAIN_TYPE));
        }
        setOfileNameFound(false);
    }


    @Override
    public void setProgramName(String programName) {

        this.programName = programName;
        Response response = target.path("ocssw").path("ocsswSetProgramName").path(jobId).request().put(Entity.entity(programName, MediaType.TEXT_PLAIN_TYPE));
    }


    @Override
    public void setIfileName(String ifileName) {
        this.ifileName = ifileName;
        setOfileNameFound(false);
        ofileName = null;
        if (uploadIFile(ifileName)) {
            ifileUploadSuccess = true;
        } else {
            ifileUploadSuccess = false;
        }

    }


    private void updateProgramName(String programName) {
        this.programName = programName;
        setXmlFileName(programName + ".xml");
    }


    public boolean uploadIFile(String ifileName) {

        ifileUploadSuccess = false;

        VisatApp visatApp = VisatApp.getApp();

        ProgressMonitorSwingWorker pmSwingWorker = new ProgressMonitorSwingWorker(visatApp.getMainFrame(),
                "OCSSW Remote Server File Upload") {

            @Override
            protected Void doInBackground(ProgressMonitor pm) throws Exception {

                pm.beginTask("Uploading file '" + ifileName + "' to the remote server and getting ofile name", 2);

                pm.worked(1);
                final FileDataBodyPart fileDataBodyPart = new FileDataBodyPart("file", new File(ifileName));
                final MultiPart multiPart = new FormDataMultiPart()
                        //.field("ifileName", ifileName)
                        .bodyPart(fileDataBodyPart);
                Response response = target.path("fileServices").path("uploadFile").path(jobId).request().post(Entity.entity(multiPart, MediaType.MULTIPART_FORM_DATA_TYPE));
                if (response.getStatus() == Response.ok().build().getStatus()) {
                    ifileUploadSuccess = true;
                }
                return null;
            }
        };
        pmSwingWorker.executeWithBlocking();
        return ifileUploadSuccess;
    }

    public boolean uploadClientFile(String fileName) {

        ifileUploadSuccess = false;


        VisatApp visatApp = VisatApp.getApp();

        ProgressMonitorSwingWorker pmSwingWorker = new ProgressMonitorSwingWorker(visatApp.getMainFrame(),
                "OCSSW Remote Server File Upload") {

            @Override
            protected Void doInBackground(ProgressMonitor pm) throws Exception {

                pm.beginTask("Uploading file '" + fileName + "' to the remote server ", 10);

                pm.worked(1);
                final FileDataBodyPart fileDataBodyPart = new FileDataBodyPart("file", new File(fileName));
                final MultiPart multiPart = new FormDataMultiPart()
                        //.field("ifileName", ifileName)
                        .bodyPart(fileDataBodyPart);
                Response response = target.path("fileServices").path("uploadClientFile").path(jobId).request().post(Entity.entity(multiPart, MediaType.MULTIPART_FORM_DATA_TYPE));
                try {
                    String fileTypeString = Files.probeContentType(new File(fileName).toPath());
                    if (fileTypeString.equals(MediaType.TEXT_PLAIN)) {
                        uploadListedFiles(pm, fileName);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    pm.done();
                } finally {
                    pm.done();
                }
                if (response.getStatus() == Response.ok().build().getStatus()) {
                    ifileUploadSuccess = true;
                }
                return null;
            }
        };
        pmSwingWorker.executeWithBlocking();
        System.out.println("upload process is done: " + pmSwingWorker.isDone());
        return ifileUploadSuccess;

    }

    /**
     * This method uploads list of files provided in the text file.
     *
     * @param fileName is the name of the text file that contains list of input files.
     * @return true if all files uploaded successfully.
     */
    public String uploadListedFiles(ProgressMonitor pm, String fileName) {

        File file = new File(fileName);
        StringBuilder sb = new StringBuilder();
        Scanner scanner = null;
        ArrayList<String> fileList = new ArrayList<>();

        try {
            scanner = new Scanner(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        int fileCount = 0;
        while (scanner.hasNextLine()) {
            fileCount++;
            fileList.add(scanner.nextLine());
        }
        scanner.close();

        pm.beginTask("Uploading " + fileCount + " files to the remote server ...", fileCount);

        FileDataBodyPart fileDataBodyPart;
        MultiPart multiPart;
        Response response;

        boolean fileUploadSuccess = true;

        int numberOfTasksWorked = 1;

        for (String nextFileName : fileList) {

            pm.setSubTaskName("Uploading " + nextFileName + " to the remote server ...");

            fileDataBodyPart = new FileDataBodyPart("file", new File(nextFileName));
            multiPart = new FormDataMultiPart()
                    //.field("ifileName", ifileName)
                    .bodyPart(fileDataBodyPart);
            response = target.path("fileServices").path("uploadClientFile").path(jobId).request().post(Entity.entity(multiPart, MediaType.MULTIPART_FORM_DATA_TYPE));
            if (response.getStatus() == Response.ok().build().getStatus()) {
                fileUploadSuccess = fileUploadSuccess & true;
                sb.append(nextFileName.substring(nextFileName.lastIndexOf(File.separator) + 1) + "\n");
            } else {
                fileUploadSuccess = fileUploadSuccess & false;
            }
            pm.worked(numberOfTasksWorked++);
        }
        String fileNames = sb.toString();


        if (fileUploadSuccess) {
            return fileNames;
        } else {
            return null;
        }


    }


    @Override
    public String getOfileName(String ifileName) {

        if (programName.equals("l3bindump")) {
            return ifileName + ".xml";
        }

        if (isOfileNameFound()) {
            return ofileName;
        }


        if (!getIfileName().equals(ifileName)) {
            this.setIfileName(ifileName);
        }

        if (ifileUploadSuccess) {
            JsonObject jsonObject = target.path("ocssw").path("getOfileName").path(jobId).request(MediaType.APPLICATION_JSON_TYPE).get(JsonObject.class);
            ofileName = jsonObject.getString("ofileName");
            missionName = jsonObject.getString("missionName");
            fileType = jsonObject.getString("fileType");
            updateProgramName(jsonObject.getString("programName"));
            ofileName = ifileName.substring(0, ifileName.lastIndexOf(File.separator) + 1) + ofileName;
            if (ofileName == null || missionName == null || fileType == null) {
                setOfileNameFound(false);
                return null;
            }
            setOfileNameFound(true);
            return ofileName;
        } else {
            setOfileNameFound(false);
            return null;
        }

    }

    @Override
    public String getOfileName(String ifileName, String[] options) {

        if (programName.equals("l3bindump")) {
            return ifileName + ".xml";
        }

        if (isOfileNameFound()) {
            return ofileName;
        }

        this.setIfileName(ifileName);

        if (ifileUploadSuccess) {
            JsonObject jsonObjectForUpload = getNextLevelNameJsonObject(ifileName, options);
            target.path("ocssw").path("uploadNextLevelNameParams").path(jobId).request().post(Entity.entity(jsonObjectForUpload, MediaType.APPLICATION_JSON_TYPE));
            JsonObject jsonObject = target.path("ocssw").path("getOfileName").path(jobId).request(MediaType.APPLICATION_JSON_TYPE).get(JsonObject.class);
            ofileName = jsonObject.getString("ofileName");
            missionName = jsonObject.getString("missionName");
            fileType = jsonObject.getString("fileType");
            updateProgramName(jsonObject.getString("programName"));
            ofileName = ifileName.substring(0, ifileName.lastIndexOf(File.separator) + 1) + ofileName;

            if (ofileName == null || missionName == null || fileType == null) {
                setOfileNameFound(false);
                return null;
            }
            setOfileNameFound(true);
            return ofileName;
        } else {
            setOfileNameFound(false);
            return null;
        }
    }

    @Override
    public String getOfileName(String ifileName, String programName, String suiteValue) {

        if (isOfileNameFound()) {
            return ofileName;
        }

        this.programName = programName;
        String[] additionalOptions = {"--suite=" + suiteValue};
        return getOfileName(ifileName, additionalOptions);
    }

    private JsonObject getNextLevelNameJsonObject(String ifileName, String[] additionalOptions) {
        String additionalOptionsString = "";
        for (String s : additionalOptions) {
            additionalOptionsString = additionalOptionsString + s + " ; ";
        }
        JsonObject jsonObject = Json.createObjectBuilder().add("ifileName", ifileName)
                .add("programName", programName)
                .add("additionalOptions", additionalOptionsString)
                .build();
        return jsonObject;
    }

    private JsonArray transformToJsonArray(String[] commandArray) {

        JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();

        for (String option : commandArray) {
            jsonArrayBuilder.add(option);
        }

        return jsonArrayBuilder.build();
    }

    @Override
    public ProcessObserver getOCSSWProcessObserver(Process process, String processName, ProgressMonitor progressMonitor) {
        RemoteProcessObserver remoteProcessObserver = new RemoteProcessObserver(process, processName, progressMonitor);
        remoteProcessObserver.setJobId(jobId);
        return remoteProcessObserver;
    }

    @Override
    public boolean isMissionDirExist(String missionName) {
        Boolean isMissionExist = target.path("ocssw").path("isMissionDirExist").path(missionName).request().get(Boolean.class);
        return isMissionExist.booleanValue();
    }

    @Override
    public String[] getMissionSuites(String missionName, String programName) {
        if (missionName == null) {
            return null;
        }
        return target.path("ocssw").path("missionSuites").path(missionName).path(programName).request(MediaType.APPLICATION_JSON_TYPE).get(String[].class);
    }

    /**
     * this method returns a command array for execution.
     * the array is constructed using the paramList data and input/output files.
     * the command array structure is: full pathname of the program to be executed, input file name, params in the required order and finally the output file name.
     * assumption: order starts with 1
     *
     * @return
     */
    @Override
    public Process execute(ProcessorModel processorModel) {

        this.processorModel = processorModel;

        Process seadasProcess = new SeadasProcess(ocsswInfo, jobId);

        JsonObject commandArrayJsonObject = null;

        String programName = processorModel.getProgramName();

        if (programName.equals(MLP_PROGRAM_NAME)) {
            return executeMLP(processorModel);
        } else {
            //todo implement par file uploading for programs other than mlp
            if (processorModel.acceptsParFile() && programName.equals(MLP_PROGRAM_NAME)) {
                String parString = processorModel.getParamList().getParamString("\n");
                File parFile = writeMLPParFile(convertParStringForRemoteServer(parString));
                target.path("ocssw").path("uploadMLPParFile").path(jobId).request().put(Entity.entity(parFile, MediaType.APPLICATION_OCTET_STREAM_TYPE));
            } else {
                commandArrayJsonObject = getJsonFromParamList(processorModel.getParamList());
                //this is to make sure that all necessary files are uploaded to the server before execution
                prepareToRemoteExecute();
                Response response = target.path("ocssw").path("executeOcsswProgramOnDemand").path(jobId).path(programName).request().put(Entity.entity(commandArrayJsonObject, MediaType.APPLICATION_JSON_TYPE));
                if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                    setProcessExitValue(0);
                }
            }

            boolean serverProcessStarted = false;

            String processStatus = "-100";
            while (!serverProcessStarted) {
                switch (processStatus) {
                    case PROCESS_STATUS_NONEXIST:
                        serverProcessStarted = false;
                        break;
                    case PROCESS_STATUS_STARTED:
                        serverProcessStarted = true;
                        break;
                    case PROCESS_STATUS_COMPLETED:
                        serverProcessStarted = true;
                        break;
                    case PROCESS_STATUS_FAILED:
                        setProcessExitValue(1);
                        serverProcessStarted = true;
                        break;
                }
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                processStatus = target.path("ocssw").path("processStatus").path(jobId).request().get(String.class);
            }
            return seadasProcess;
        }
    }

    @Override
    public Process executeSimple(ProcessorModel processorModel) {
        Process seadasProcess = new SeadasProcess(ocsswInfo, jobId);

        JsonObject commandArrayJsonObject = getJsonFromParamList(processorModel.getParamList());
        Response response = target.path("ocssw").path("executeOcsswProgramSimple").path(jobId).path(processorModel.getProgramName()).request().put(Entity.entity(commandArrayJsonObject, MediaType.APPLICATION_JSON_TYPE));
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            setProcessExitValue(0);
        }

        boolean serverProcessCompleted = false;

        String processStatus = "-100";
        while (!serverProcessCompleted) {
            switch (processStatus) {
                case PROCESS_STATUS_NONEXIST:
                    serverProcessCompleted = false;
                    break;
                case PROCESS_STATUS_STARTED:
                    serverProcessCompleted = false;
                    break;
                case PROCESS_STATUS_COMPLETED:
                    serverProcessCompleted = true;
                    break;
                case PROCESS_STATUS_FAILED:
                    setProcessExitValue(1);
                    serverProcessCompleted = true;
                    break;
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            processStatus = target.path("ocssw").path("processStatus").path(jobId).request().get(String.class);
        }
        return seadasProcess;
    }


    public InputStream executeAndGetStdout(ProcessorModel processorModel) {
        InputStream responceStream = null;
        JsonObject commandArrayJsonObject = getJsonFromParamList(processorModel.getParamList());
        Response response = target.path("ocssw").path("executeOcsswProgramAndGetStdout").path(jobId).path(processorModel.getProgramName()).request().put(Entity.entity(commandArrayJsonObject, MediaType.APPLICATION_JSON_TYPE));
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            response = target.path("fileServices").path("downloadAncFileList").path(jobId).request().get(Response.class);
            responceStream = (InputStream) response.getEntity();
        }
        return responceStream;
    }

    @Override
    public void waitForProcess() {
        boolean serverProcessCompleted = false;

        String processStatus = PROCESS_STATUS_NONEXIST;
        while (!serverProcessCompleted) {
            switch (processStatus) {
                case PROCESS_STATUS_NONEXIST:
                    serverProcessCompleted = false;
                    break;
                case PROCESS_STATUS_STARTED:
                    serverProcessCompleted = false;
                    break;
                case PROCESS_STATUS_COMPLETED:
                    serverProcessCompleted = true;
                    setProcessExitValue(0);
                    break;
                case PROCESS_STATUS_FAILED:
                    setProcessExitValue(1);
                    serverProcessCompleted = true;
                    break;
                default:
                    serverProcessCompleted = false;
            }
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            processStatus = target.path("ocssw").path("processStatus").path(jobId).request().get(String.class);
        }
    }

    @Override
    public void getOutputFiles(ProcessorModel processorModel) {

        VisatApp visatApp = VisatApp.getApp();

        ProgressMonitorSwingWorker pmSwingWorker = new ProgressMonitorSwingWorker(visatApp.getMainFrame(),
                "OCSSW Remote Server File Download") {

            @Override
            protected Void doInBackground(ProgressMonitor pm) throws Exception {

                JsonObject commandArrayJsonObject = null;

                if (programName.equals(MLP_PROGRAM_NAME)) {
                    JsonObject outputFilesList = target.path("ocssw").path("getMLPOutputFiles").path(jobId).request().get(JsonObject.class);
                    downloadMLPOutputFiles(outputFilesList, pm);
                } else {
                    commandArrayJsonObject = getJsonFromParamList(processorModel.getParamList());
                    downloadCommonFiles(commandArrayJsonObject);
                }
                return null;
            }
        };
        pmSwingWorker.executeWithBlocking();
    }


    private void downloadCommonFiles(JsonObject paramJsonObject) {
        Set commandArrayKeys = paramJsonObject.keySet();
        String param;
        String ofileFullPathName, ofileName;
        try {
            Object[] array = (Object[]) commandArrayKeys.toArray();
            int i = 0;
            String[] commandArray = new String[commandArrayKeys.size() + 1];
            commandArray[i++] = programName;
            for (Object element : array) {
                String elementName = (String) element;
                param = paramJsonObject.getString((String) element);
                if (elementName.contains("OFILE")) {
                    if (param.indexOf("=") != -1) {
                        StringTokenizer st = new StringTokenizer(param, "=");
                        String paramName = st.nextToken();
                        String paramValue = st.nextToken();
                        ofileFullPathName = paramValue;

                    } else {
                        ofileFullPathName = param;
                    }
                    ofileName = ofileFullPathName.substring(ofileFullPathName.lastIndexOf(File.separator) + 1);
                    Response response = target.path("fileServices").path("downloadFile").path(jobId).path(ofileName).request().get(Response.class);
                    InputStream responceStream = (InputStream) response.getEntity();
                    SeadasFileUtils.writeToFile(responceStream, ofileFullPathName);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Process executeMLP(ProcessorModel processorModel) {
        Process seadasProcess = new SeadasProcess(ocsswInfo, jobId);
        String parString = processorModel.getParamList().getParamString("\n");
        File parFile = writeMLPParFile(convertParStringForRemoteServer(parString));
        target.path("ocssw").path("uploadMLPParFile").path(jobId).request().put(Entity.entity(parFile, MediaType.APPLICATION_OCTET_STREAM_TYPE));


        boolean serverProcessStarted = false;

        String processStatus = "-100";
        while (!serverProcessStarted) {
            switch (processStatus) {
                case PROCESS_STATUS_NONEXIST:
                    serverProcessStarted = false;
                    break;
                case PROCESS_STATUS_STARTED:
                    serverProcessStarted = true;
                    setProcessExitValue(1);
                    break;
                case PROCESS_STATUS_COMPLETED:
                    serverProcessStarted = true;
                    setProcessExitValue(1);
                    break;
                case PROCESS_STATUS_FAILED:
                    setProcessExitValue(1);
                    serverProcessStarted = true;
                    break;
                default:
                    serverProcessStarted = false;
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("process status before: " + processStatus);
            processStatus = target.path("ocssw").path("processStatus").path(jobId).request().get(String.class);
            System.out.println("process status after: " + processStatus);
        }
        return seadasProcess;
    }

    private void downloadMLPOutputFiles(JsonObject paramJsonObject, ProgressMonitor pm) {

        if (ofileDir == null) {
            ofileDir = ifileDir;
        }

        pm.beginTask("Downloading output files from the remote server to " + ofileDir, paramJsonObject.size());
        pm.worked(1);

        Set commandArrayKeys = paramJsonObject.keySet();
        String param;
        String ofileFullPathName, ofileName;
        try {
            Object[] array = (Object[]) commandArrayKeys.toArray();
            int i = 0;
            String[] commandArray = new String[commandArrayKeys.size() + 1];
            commandArray[i++] = programName;

            int numberOfTasksWorked = 1;

            for (Object element : array) {
                String elementName = (String) element;
                param = paramJsonObject.getString((String) element);
                if (elementName.contains("OFILE")) {
                    if (param.indexOf("=") != -1) {
                        StringTokenizer st = new StringTokenizer(param, "=");
                        String paramName = st.nextToken();
                        String paramValue = st.nextToken();
                        ofileFullPathName = paramValue;

                    } else {
                        ofileFullPathName = param;
                    }
                    ofileName = ofileFullPathName.substring(ofileFullPathName.lastIndexOf(File.separator) + 1);

                    pm.setSubTaskName("Downloading file '" + ofileName + " to " + ofileDir);

                    Response response = target.path("fileServices").path("downloadMLPOutputFile").path(jobId).path(ofileName).request().get(Response.class);
                    InputStream responceStream = (InputStream) response.getEntity();
                    ofileFullPathName = ofileDir + File.separator + ofileName;
                    SeadasFileUtils.writeToFile(responceStream, ofileFullPathName);
                    pm.worked(numberOfTasksWorked++);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Process execute(ParamList paramListl) {
        JsonObject commandArrayJsonObject = getJsonFromParamList(paramListl);
        Response response = target.path("ocssw").path("executeOcsswProgram").path(jobId).request().put(Entity.entity(commandArrayJsonObject, MediaType.APPLICATION_JSON_TYPE));
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            Response output = target.path("fileServices").path("downloadFile").path(jobId).request().get(Response.class);
            final InputStream responseStream = (InputStream) output.getEntity();
            SeadasFileUtils.writeToFile(responseStream, ofileName);
        }
        Process Process = new SeadasProcess(ocsswInfo, jobId);
        return Process;
    }

    protected JsonObject getJsonFromParamList(ParamList paramList) {

        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();


        Iterator itr = paramList.getParamArray().iterator();
        Response response;

        ParamInfo option;
        String commandItem;
        String fileName;

        while (itr.hasNext()) {
            option = (ParamInfo) itr.next();
            commandItem = null;
            if (option.getType() != ParamInfo.Type.HELP) {
                if (option.getUsedAs().equals(ParamInfo.USED_IN_COMMAND_AS_ARGUMENT)) {
                    if (option.getValue() != null && option.getValue().length() > 0) {
                        commandItem = option.getValue();
                    }
                } else if (option.getUsedAs().equals(ParamInfo.USED_IN_COMMAND_AS_OPTION) && !option.getDefaultValue().equals(option.getValue())) {
                    commandItem = option.getName() + "=" + option.getValue();
                    if (option.getType().equals(ParamInfo.Type.OFILE)) {
                        ofileDir = option.getValue().substring(0, option.getValue().lastIndexOf(File.separator));
                    }
                    if (option.getType().equals(ParamInfo.Type.IFILE)) {
                        fileName = option.getValue().substring(option.getValue().lastIndexOf(File.separator) + 1);
                        ifileDir = option.getValue().substring(0, option.getValue().lastIndexOf(File.separator));
                        response = ocsswClient.getServicePathForFileVerification(jobId).queryParam("fileName", option.getValue()).request().get();
                        if (response.getStatus() != Response.Status.FOUND.getStatusCode()) {
                            uploadClientFile(option.getValue());
                        } else {
                            ifileUploadSuccess = true;
                        }
                    }
                } else if (option.getUsedAs().equals(ParamInfo.USED_IN_COMMAND_AS_FLAG) && (option.getValue().equals("true") || option.getValue().equals("1"))) {
                    if (option.getName() != null && option.getName().length() > 0) {
                        commandItem = option.getName();
                    }
                }
            }
            //need to send both item name and its type to accurately construct the command array on the server
            if (commandItem != null) {
                jsonObjectBuilder.add(option.getName() + "_" + option.getType(), commandItem);
            }

        }
        return jsonObjectBuilder.build();
    }

    private void prepareToRemoteExecute() {
        Response response;
        String fileExtensions = processorModel.getImplicitInputFileExtensions();
        if (fileExtensions != null) {
            StringTokenizer st = new StringTokenizer(fileExtensions, ",");
            String fileExtension;
            String fileNameBase = ifileName.substring(ifileName.lastIndexOf(File.separator) + 1, ifileName.lastIndexOf("."));
            String fileNameToUpload;
            while (st.hasMoreTokens()) {
                fileExtension = st.nextToken().trim();
                fileNameToUpload = ifileDir + File.separator + fileNameBase + "." + fileExtension;
                response = ocsswClient.getServicePathForFileVerification(jobId).queryParam("fileName",fileNameToUpload).request().get();
                if (response.getStatus() != Response.Status.FOUND.getStatusCode()) {
                    uploadClientFile(fileNameToUpload);
                }
            }
        }
    }

    private JsonArray getJsonFromParamListOld(ParamList paramList) {
        JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();

        Iterator itr = paramList.getParamArray().iterator();

        ParamInfo option;
        String optionValue;
        while (itr.hasNext()) {
            option = (ParamInfo) itr.next();
            optionValue = option.getValue();
            if (option.getType() != ParamInfo.Type.HELP) {
                if (option.getUsedAs().equals(ParamInfo.USED_IN_COMMAND_AS_ARGUMENT)) {
                    if (option.getValue() != null && option.getValue().length() > 0) {
                        jsonArrayBuilder.add(optionValue);
                    }
                } else if (option.getUsedAs().equals(ParamInfo.USED_IN_COMMAND_AS_OPTION) && !option.getDefaultValue().equals(option.getValue())) {
                    jsonArrayBuilder.add(option.getName() + "=" + optionValue);
                } else if (option.getUsedAs().equals(ParamInfo.USED_IN_COMMAND_AS_FLAG) && (option.getValue().equals("true") || option.getValue().equals("1"))) {
                    if (option.getName() != null && option.getName().length() > 0) {
                        jsonArrayBuilder.add(option.getName());
                    }
                }
            }
        }
        JsonArray jsonCommandArray = jsonArrayBuilder.build();
        return jsonCommandArray;
    }

    protected String convertParStringForRemoteServer(String parString) {
        StringTokenizer st1 = new StringTokenizer(parString, "\n");
        StringTokenizer st2;
        StringBuilder stringBuilder = new StringBuilder();
        String token;
        String key, value;
        String fileTypeString;
        while (st1.hasMoreTokens()) {
            token = st1.nextToken();
            if (token.contains("=")) {
                st2 = new StringTokenizer(token, "=");
                key = st2.nextToken();
                value = st2.nextToken();
                if (new File(value).exists() && !new File(value).isDirectory()) {
                    //if item is ifile
                    if (key.equals(processorModel.getPrimaryInputFileOptionName())) {
                        ifileDir = value.substring(0, value.lastIndexOf(File.separator));
                    }
                    uploadClientFile(value);
                    value = value.substring(value.lastIndexOf(File.separator) + 1);

                } else if (key.equals(MLP_PAR_FILE_ODIR_KEY_NAME) && new File(value).isDirectory()) {
                    ofileDir = value;
                    //if item is ofile
                } else if (key.equals(processorModel.getPrimaryOutputFileOptionName())) {
                    ofileDir = value.substring(0, value.lastIndexOf(File.separator));
                }
                token = key + "=" + value;
            }
            stringBuilder.append(token);
            stringBuilder.append("\n");
        }

        String newParString = stringBuilder.toString();
        //remove empty lines
        String adjusted = newParString.replaceAll("(?m)^[ \t]*\r?\n", "");
        return adjusted;
    }

    protected File writeParFile(String parString) {

        try {

            final File tempFile = File.createTempFile(programName + "-tmpParFile", ".par");
            String parFileLocation = tempFile.getAbsolutePath();
            System.out.println(tempFile.getAbsoluteFile());
            tempFile.deleteOnExit();
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(tempFile);
                fileWriter.write(parString);
            } finally {
                if (fileWriter != null) {
                    fileWriter.close();
                }
            }
            return tempFile;

        } catch (IOException e) {
            SeadasLogger.getLogger().warning("parfile is not created. " + e.getMessage());
            return null;
        }
    }

    protected File writeMLPParFile(String parString) {

        try {

            final File tempFile = File.createTempFile(MLP_PAR_FILE_NAME, ".par");
            String parFileLocation = tempFile.getAbsolutePath();
            System.out.println(tempFile.getAbsoluteFile());
            tempFile.deleteOnExit();
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(tempFile);
                fileWriter.write(parString);
            } finally {
                if (fileWriter != null) {
                    fileWriter.close();
                }
            }
            return tempFile;

        } catch (IOException e) {
            SeadasLogger.getLogger().warning("parfile is not created. " + e.getMessage());
            return null;
        }
    }

    @Override
    public Process execute(String[] commandArray) {
        return null;
    }

    @Override
    public Process execute(String programName, String[] commandArrayParams) {
        return null;
    }

    @Override
    public void setCommandArrayPrefix() {

    }

    @Override
    public void setCommandArraySuffix() {

    }

    @Override
    public void setMissionName(String missionName) {

    }

    @Override
    public void setFileType(String fileType) {

    }

    public static void retrieveServerSharedDirName() {
        OCSSWClient ocsswClient = new OCSSWClient();
        WebTarget target = ocsswClient.getOcsswWebTarget();
        OCSSW.getOCSSWInstance().setServerSharedDirName(target.path("file").path("test").request(MediaType.TEXT_PLAIN).get(String.class));
    }

}

