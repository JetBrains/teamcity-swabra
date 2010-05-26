package jetbrains.buildServer.swabra.web;

import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.apache.log4j.Logger;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.Iterator;
import java.util.Map;

/**
 * User: vbedrosova
 * Date: 19.05.2010
 * Time: 15:20:30
 */
public class FileUploadController extends BaseFormXmlController {
  @NonNls
  private static final Logger LOG = Logger.getLogger(FileUploadController.class);
  @NonNls
  private static final String CONTROLLER_PATH = "/fileUpload.html";
  @NotNull
  private final WebControllerManager myWebControllerManager;
  private File myTempFolder;


  public FileUploadController(@NotNull final WebControllerManager webControllerManager) {
    myWebControllerManager = webControllerManager;
  }

  public void register() {
    myWebControllerManager.registerController(CONTROLLER_PATH, this);
    myTempFolder = new File(FileUtil.getTempDirectory(), "fileUpload");
    FileUtil.delete(myTempFolder);
    myTempFolder.mkdirs();
    LOG.debug("Registered on " + CONTROLLER_PATH + " with " + myTempFolder.getAbsolutePath() + "temp folder");
  }

  @Override
  protected ModelAndView doGet(HttpServletRequest request, HttpServletResponse response) {
    return null;
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response, Element xmlResponse) {
    //TODO: process permissions 
    if (request instanceof MultipartHttpServletRequest) {
      final MultipartHttpServletRequest mRequest = (MultipartHttpServletRequest) request;

      final Map files = mRequest.getFileMap();
      for (Iterator it = mRequest.getFileNames(); it.hasNext();) {
        final MultipartFile file = (MultipartFile) files.get(it.next());
        if (file.isEmpty()) {
          logFailedToUpload(file, "file \"" + file.getName() + "\" is empty", null);
          addUploadedFile(xmlResponse, file, null);
          continue;
        }
        final File tempFile = new File(myTempFolder, file.getOriginalFilename());
        FileUtil.delete(tempFile);
        try {
          file.transferTo(tempFile);
        } catch (Exception e) {
          logFailedToUpload(file, "exception occurred", e);
          addUploadedFile(xmlResponse, file, null);
          continue;
        }

        LOG.debug("Successfully uploaded " + file.getOriginalFilename() + " to " + tempFile.getAbsolutePath());
        addUploadedFile(xmlResponse, file, tempFile);
      }
    } else {
      LOG.warn("Recieved non-multipart request from " + request.getRemoteHost());
    }
  }

  private void addUploadedFile(Element xmlResponse, MultipartFile file, File tempFile) {
    final Element uploadedFileElem = new Element("uploadedFile");
    xmlResponse.addContent((Content) uploadedFileElem);
    uploadedFileElem.setAttribute("name", file.getName());
    if (tempFile != null) {
      uploadedFileElem.setAttribute("success", "true");
      uploadedFileElem.setAttribute("path", tempFile.getAbsolutePath());
    } else {
      uploadedFileElem.setAttribute("success", "false");
    }
  }

  private static void logFailedToUpload(MultipartFile file, String message, Exception e) {
    LOG.warn("Failed to upload " + file.getOriginalFilename() + ": " + message, e);
  }
}
