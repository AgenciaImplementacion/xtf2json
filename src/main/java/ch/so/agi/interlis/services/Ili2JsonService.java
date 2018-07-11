/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.so.agi.interlis.services;

import ch.ehi.basics.logging.EhiLogger;
import ch.interlis.ili2c.Ili2cException;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.gui.UserSettings;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ilirepository.IliManager;
import ch.interlis.iox.IoxException;
import ch.interlis.iox_j.IoxUtility;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Service to convert xtf to json
 *
 * @author grand
 */
@Service
public class Ili2JsonService {

    private Environment env;
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private static String version = null;

    public Environment getEnv() {
        return env;
    }

    public void setEnv(Environment env) {
        this.env = env;
    }

    private String[] getIliDirs() {

        String ili_dir = System.getenv("interlis_ilidir");
        if (ili_dir == null) {
            ili_dir = env.getProperty("ch.so.agi.interlis.paths.iliDir");
        }

        String ili_repository = "https://repositorio.proadmintierra.info/";
        String ilidir_separator = ";";
        String default_ilidirs = ili_dir + ilidir_separator + ili_repository;

        return default_ilidirs.split(ilidir_separator);
    }

    public void setDefaultIli2cPathMap(ch.ehi.basics.settings.Settings settings) {
        HashMap<String, String> pathmap = new HashMap<String, String>();

        String ili_dir = System.getenv("interlis_ilidir");
        if (ili_dir == null) {
            ili_dir = env.getProperty("ch.so.agi.interlis.paths.iliDir");
        }

        pathmap.put(ili_dir, null);
        settings.setTransientObject(UserSettings.ILIDIRS_PATHMAP, pathmap);
    }

    public String getOgrDirPath() {

        return env.getProperty("ch.so.agi.interlis.paths.ogrPath");

    }

    private static String getVersion() {
        if (version == null) {
            java.util.ResourceBundle resVersion = java.util.ResourceBundle.getBundle("ch.interlis.ili2c.Version");
            // Major version numbers identify significant functional changes.
            // Minor version numbers identify smaller extensions to the
            // funclog.error(e.getMessage());tionality.
            // Micro versions are even finer grained versions.
            StringBuilder ret = new StringBuilder(20);
            ret.append(resVersion.getString("versionMajor"));
            ret.append('.');
            ret.append(resVersion.getString("versionMinor"));
            ret.append('.');
            ret.append(resVersion.getString("versionMicro"));
            ret.append('-');
            String branch = ch.ehi.basics.tools.StringUtility.purge(resVersion.getString("versionBranch"));
            if (branch != null) {
                ret.append(branch);
                ret.append('-');
            }
            ret.append(resVersion.getString("versionDate"));
            version = ret.toString();
        }
        return version;
    }

    private List<String> getModelsXFT(String pathXTF) {
        List<String> modelsList = null;
        try {
            File fXTF = new File(pathXTF);

            if (fXTF.exists()) {
                modelsList = IoxUtility.getModels(fXTF);
            }
        } catch (IoxException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }

        return modelsList;
    }

    public ArrayList getIliModels(String pathXTF) {

        ArrayList listIliFiles = new ArrayList();
        EhiLogger.getInstance().setTraceFilter(false);
        IliManager m = new IliManager();

        // get models and repositories
        ArrayList<String> requiredModels = new ArrayList<String>(getModelsXFT(pathXTF));
        String[] requiredRepositories = getIliDirs();

        // Set repositories
        //m.setRepositories(new String[]{ILI_DIR, "https://repositorio.proadmintierra.info/"});
        m.setRepositories(requiredRepositories);

        try {
            Configuration config = m.getConfig(requiredModels, 0.0);
            if (config != null) {
                //ch.interlis.ili2c.Ili2c.logIliFiles(config);

                java.util.Iterator filei = config.iteratorFileEntry();
                while (filei.hasNext()) {

                    ch.interlis.ili2c.config.FileEntry file = (ch.interlis.ili2c.config.FileEntry) filei.next();
                    // get ili file model
                    listIliFiles.add(file.getFilename());
                    EhiLogger.logState("ilifile <" + file.getFilename() + ">");
                }
            }
        } catch (Ili2cException e) {
            EhiLogger.logError(e);
        }

        return listIliFiles;
    }

    public List<String> executeCommand(List command) {

        List outCommand = new ArrayList();

        try {

            ProcessBuilder builder = new ProcessBuilder(command);
            //builder.inheritIO();
            //pb.directory(new File(workingDir));

            Process process = builder.start();
            process.waitFor();

            InputStream is = process.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;

            while ((line = br.readLine()) != null) {
                outCommand.add(line);
            }

        } catch (IOException | InterruptedException ex) {
            log.error(ex.getMessage());
        }

        return outCommand;
    }

    public TransferDescription getTansfDesc(String pathIli) {

        UserSettings settings = new UserSettings();
        setDefaultIli2cPathMap(settings);

        String ili_dir = System.getenv("interlis_ilidir");
        if (ili_dir == null) {
            ili_dir = env.getProperty("ch.so.agi.interlis.paths.iliDir");
        }
        String uploadDir = FilenameUtils.getFullPath(pathIli);

        settings.setIlidirs(uploadDir + ";" + "http://models.interlis.ch/" + ";" + ili_dir);

        Configuration config = new Configuration();
        FileEntry file = new FileEntry(pathIli, FileEntryKind.ILIMODELFILE);

        config.addFileEntry(file);
        config.setAutoCompleteModelList(true);

        TransferDescription td = ch.interlis.ili2c.Main.runCompiler(config, settings);

        return td;
    }

    public Map getClassesTransDesc(TransferDescription td, String nameModel) {

        HashMap items = ch.interlis.iom_j.itf.ModelUtilities.getTagMap(td);
        Map mapClasses = new HashMap();

        for (Iterator it = items.entrySet().iterator(); it.hasNext();) {

            Map.Entry<String, Table> entry = (Map.Entry<String, Table>) it.next();
            String key = entry.getKey();
            //Table value = entry.getValue();

            if (key.split("\\.").length > 0) {
                if (!"INTERLIS".equals(key.split("\\.")[0])) {
                    mapClasses.put(key, nameModel);
                }
            }
        }

        return mapClasses;
    }

    public void td2imd(String pathIli, TransferDescription td) {

        String APP_NAME = "ili2json";

        Configuration config = new Configuration();
        File iliFile = new File(pathIli);
        String dirIliFile = iliFile.getParent();
        String imdFile = iliFile.getName().split(".ili")[0] + ".imd";
        String pathImdFile = dirIliFile + File.separator + imdFile;

        // set config
        config.setOutputFile(pathImdFile);

        boolean validTd = td != null ? Boolean.TRUE : Boolean.FALSE;

        // convertion to imd
        if (validTd) {
            ch.interlis.ili2c.generator.ImdGenerator.generate(
                    new java.io.File(config.getOutputFile()),
                    td,
                    APP_NAME + "-" + getVersion());
        } else {

            // It's not possible convert file
            log.error("It's not possible convert file");
        }

    }

    public void td2imd(String pathIli, TransferDescription td, String outputDir) {

        String APP_NAME = "ili2json";
        Configuration config = new Configuration();
        File iliFile = new File(pathIli);
        String imdFile = iliFile.getName().split(".ili")[0] + ".imd";
        String pathImdFile = outputDir + File.separator + imdFile;

        // set config
        config.setOutputFile(pathImdFile);

        boolean validTd = td != null ? Boolean.TRUE : Boolean.FALSE;

        // convertion to imd
        if (validTd) {
            ch.interlis.ili2c.generator.ImdGenerator.generate(
                    new java.io.File(config.getOutputFile()),
                    td,
                    APP_NAME + "-" + getVersion());
        } else {

            // It's not possible convert file
            log.error("It's not possible convert file");
        }

    }

    private List getTablesXTF(String pathXTF) {

        List parameters = new ArrayList();
        parameters.add(getOgrDirPath() + File.separator + "ogrinfo");
        parameters.add("-q");
        parameters.add("-so");
        parameters.add(pathXTF);

        List<String> outTables = executeCommand(parameters);

        List tables = new ArrayList();

        outTables.forEach((outTable) -> {
            tables.add(outTable.split(":")[1].trim());
        });

        return tables;
    }

    private String table2json(String pathXTF, String table, String model) {
        File fileXTF = new File(pathXTF);
        String workingDir = fileXTF.getParent();

        String format = "GeoJSON";
        
        String outName = workingDir + File.separator + 
                         fileXTF.getName().substring(0,fileXTF.getName().lastIndexOf('.')) + "_" + 
                         table.substring(table.lastIndexOf('.') + 1) + ".json";

        List parameters = new ArrayList();
        parameters.add(getOgrDirPath() + File.separator + "ogr2ogr");
        parameters.add("-skipfailures");
        parameters.add("-f");
        parameters.add(format);
        parameters.add(outName);
        parameters.add(pathXTF);

        if (!model.isEmpty()) {
            parameters.add(",");
            parameters.add(workingDir + File.separator + model + ".imd");
        }

        parameters.add(table);
        executeCommand(parameters);

        // Convertion without models
        // Alert: This can generate data loss
        return outName;
    }

    public List<String> translate(String pathXTF, Map classesModels) {

        List jsonTables = new ArrayList<>();
        List<String> tables = getTablesXTF(pathXTF);

        tables.forEach((table) -> {
            if (classesModels.containsKey(table)) {
                jsonTables.add(table2json(pathXTF, table, (String) classesModels.get(table)));
            } else {
                jsonTables.add(table2json(pathXTF, table, ""));
            }
        });

        return jsonTables;
    }

    public HashMap checkGenerateFile(List<String> outputFiles) {
        HashMap checkedFiles = new HashMap();

        for (String outputFile : outputFiles) {

            File outFile = new File(outputFile);

            if (outFile.exists()) {

                Gson gson = new Gson();
                BufferedReader bufferedReader;
                try {
                    bufferedReader = new BufferedReader(new FileReader(outFile));
                    JsonObject json = new Gson().fromJson(bufferedReader, JsonObject.class);

                    JsonElement element = json.get("features");

                    if (!(element instanceof JsonNull)) {

                        // Get num of items
                        int countElements = json.get("features").getAsJsonArray().size();

                        if (countElements > 0) {

                            Map.Entry<Integer, String> entryProperties;

                            if (((JsonArray) element).get(0).getAsJsonObject().get("geometry") instanceof JsonNull) {
                                entryProperties = new AbstractMap.SimpleEntry<>(countElements, "Table");

                            } else {

                                JsonObject geomObjectJson = (JsonObject) ((JsonArray) element).get(0).getAsJsonObject().get("geometry");
                                String typeGeom = geomObjectJson.get("type").getAsString();

                                entryProperties = new AbstractMap.SimpleEntry<>(countElements, typeGeom);

                            }

                            checkedFiles.put(outputFile, entryProperties);

                        } else {
                            // Delete if have zero items
                            outFile.delete();
                        }
                    } else {
                        // Delete file if dont have info
                        outFile.delete();
                    }

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    log.error(e.getMessage());
                }
            }
        }

        return checkedFiles;
    }

    public String writeOutIli2Json(String dirOutput, String filenameInput, HashMap items) {

        String out = "";

        out += "{\"result_id\":\"" + dirOutput + "\",\"transfer\":\"" + filenameInput + "\"";

        out += ",\"spatial_datasets\": [";

        String sep = "";

        for (Iterator it = items.entrySet().iterator(); it.hasNext();) {

            Map.Entry entry = (Map.Entry) it.next();

            String key = (String) entry.getKey();
            File fileKey = new File(key);

            //int coutItems = (int) entry.getValue();
            Map.Entry entryProperties = (Map.Entry) entry.getValue();

            if (!"Table".equals((String) entryProperties.getValue())) {
                //String outItem = sep+"\"item\": {";
                String outItem = sep + "{";
                outItem += "\"key\" : \"" + fileKey.getName()
                        + "\", \"count\": " + (Integer) entryProperties.getKey()
                        + ", \"type\": \"" + (String) entryProperties.getValue();
                outItem += "\"}";

                out += outItem;
                sep = ",";

            }
        }

        out = out + "]";

        out += ",\"alphanumeric_datasets\": [";
        sep = "";

        for (Iterator it = items.entrySet().iterator(); it.hasNext();) {

            Map.Entry entry = (Map.Entry) it.next();

            String key = (String) entry.getKey();
            File fileKey = new File(key);

            //int coutItems = (int) entry.getValue();
            Map.Entry entryProperties = (Map.Entry) entry.getValue();

            if ("Table".equals((String) entryProperties.getValue())) {
                //String outItem = sep+"\"item\": {";
                String outItem = sep + "{";
                outItem += "\"key\" : \"" + fileKey.getName()
                        + "\", \"count\": " + (Integer) entryProperties.getKey()
                        + ", \"type\": \"" + (String) entryProperties.getValue();
                outItem += "\"}";

                out += outItem;
                sep = ",";

            }
        }

        out = out + "]";
        out = out + "},";

        return out;
    }
}
