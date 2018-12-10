package ch.so.agi.interlis.controllers;

import ch.interlis.ili2c.metamodel.TransferDescription;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import ch.so.agi.interlis.services.Ili2JsonService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MainController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private Environment env;

    /*
    @Autowired
    Ili2JsonService ili2json;
     */
    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String provideUploadInfo() {
        return "index.html";
    }
    
    @GetMapping("/health")
    public String health(Model model) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        String date_time = dtf.format(now);
        model.addAttribute("date_time", date_time);
        return "health";
    }
    
    @RequestMapping(value = "download/{id}/{key}/{type}", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<InputStreamResource> downloadResult(@PathVariable String id,
            @PathVariable String key,
            @PathVariable String type) throws FileNotFoundException {

        String fileName = key.substring(0, key.lastIndexOf("."));
        String returnFile = null;
        String mediaType = "application/octet-stream";
        switch (type) {
            case "json":
                returnFile = getUploadedFilesPath() + File.separator + id + File.separator + fileName + ".json";
                mediaType = "application/json";
                break;
        }

        File file = new File(returnFile);
        InputStream is = new FileInputStream(file);

        return ResponseEntity.ok().contentLength(file.length())
                .contentType(MediaType.parseMediaType(mediaType))
                .header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"")
                .body(new InputStreamResource(is));
    }

    public String getUploadedFilesPath() {
        String uploadedFilesPath = System.getenv("interlis_uploadedfiles");
        if (uploadedFilesPath == null) {
            uploadedFilesPath = env.getProperty("ch.so.agi.interlis.paths.uploadedFiles");
        }

        return uploadedFilesPath;
    }

    @RequestMapping(value = "ili2json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<String> convertIli2Json(@RequestParam("file[]") MultipartFile[] uploadfiles,
            @RequestParam(name = "model_file[]", required = false) MultipartFile[] iliFiles) {
        try {
            Ili2JsonService ili2json = new Ili2JsonService();
            ili2json.setEnv(env);

            // Create temporal directory
            String tmpDirectoryPrefix = "ili2json_";
            Path tmpDirectory = Files.createTempDirectory(Paths.get(getUploadedFilesPath()), tmpDirectoryPrefix);
            String out = "";

            Map classesModels = new HashMap();

            // Upload model files
            for (MultipartFile iliFile : iliFiles) {
                String iliFileName = iliFile.getOriginalFilename();
                if (!iliFileName.equals("")) {
                    String ilifilepath = Paths.get(tmpDirectory.toString(), iliFileName).toString();

                    try ( // Save the file locally
                            BufferedOutputStream ilistream = new BufferedOutputStream(
                                    new FileOutputStream(new File(ilifilepath)))) {
                        ilistream.write(iliFile.getBytes());
                    }

                    TransferDescription td = ili2json.getTansfDesc(ilifilepath); // Convert ili 2 imd

                    if (td != null) {

                        Map classes = ili2json.getClassesTransDesc(td,
                                iliFileName.substring(0, iliFileName.lastIndexOf('.')));

                        if (!classes.isEmpty()) {
                            classesModels.putAll(classes);
                        }

                        ili2json.td2imd(ilifilepath, td);
                    }
                }
            }

            //upload xtf
            for (MultipartFile uploadfile : uploadfiles) {

                // Get the filename and build the local file path
                String fileXTF = uploadfile.getOriginalFilename();
                String pathXTF = Paths.get(tmpDirectory.toString(), fileXTF).toString();
                String workingDir = tmpDirectory.toString();

                try (BufferedOutputStream stream
                        = new BufferedOutputStream(new FileOutputStream(new File(pathXTF)))) {
                    stream.write(uploadfile.getBytes());
                }

                ArrayList<String> iliModels = ili2json.getIliModels(pathXTF);

                iliModels.forEach((iliModel) -> {

                    TransferDescription td = ili2json.getTansfDesc(iliModel);

                    if (td != null) {

                        ili2json.td2imd(iliModel, td, workingDir);
                        String nameIliModel = new File(iliModel).getName();
                        Map classes = ili2json.getClassesTransDesc(td,
                                nameIliModel.substring(0, nameIliModel.lastIndexOf('.')));
                        if (!classes.isEmpty()) {
                            classesModels.putAll(classes);
                        }
                    }
                });

                List outFiles = ili2json.translate(pathXTF, classesModels);

                // check generate files
                HashMap items = ili2json.checkGenerateFile(outFiles);

                // get output to write
                out += ili2json.writeOutIli2Json(tmpDirectory.getFileName().getName(0).toString(), fileXTF, items);

            }

            if (out.lastIndexOf(",") != -1) {
                out = out.substring(0, out.lastIndexOf(","));
            }

            return new ResponseEntity<>("[" + out + "]", HttpStatus.OK);

        } catch (IOException e) {
            log.error(e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

}
