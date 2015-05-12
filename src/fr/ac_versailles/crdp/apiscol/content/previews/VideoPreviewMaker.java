package fr.ac_versailles.crdp.apiscol.content.previews;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import fr.ac_versailles.crdp.apiscol.content.RefreshProcessRegistry.States;
import fr.ac_versailles.crdp.apiscol.content.fileSystemAccess.ResourceDirectoryInterface;
import fr.ac_versailles.crdp.apiscol.content.resources.ResourcesLoader;
import fr.ac_versailles.crdp.apiscol.utils.FileUtils;
import fr.ac_versailles.crdp.apiscol.utils.JSonUtils;

public class VideoPreviewMaker extends AbstractPreviewMaker {

	public VideoPreviewMaker(String resourceId, String previewsRepoPath,
			String entryPoint, String previewUri) {
		super(resourceId, previewsRepoPath, entryPoint, previewUri);

	}

	@Override
	protected void createNewPreview() {
		trackingObject
				.updateStateAndMessage(States.pending,
						"The video file is being converted to html5 web-compliant formats.");
		InputStream is = null;
		String path = "templates/videopreviewwidget.html";

		is = ResourcesLoader.loadResource(path);
		if (is == null) {
			trackingObject.updateStateAndMessage(States.aborted,
					"Impossible to copy the preview template : " + path);
			return;
		}
		Map<String, String> tokens = new HashMap<String, String>();
		tokens.put("url", previewUri);
		tokens.put("preview-id", resourceId);
		MapTokenResolver resolver = new MapTokenResolver(tokens);

		Reader source = new InputStreamReader(is);

		Reader reader = new TokenReplacingReader(source, resolver);

		String htmlWidgetFilePath = previewDirectoryPath + "/widget.html";
		new File(previewDirectoryPath).mkdirs();
		FileUtils.writeDataToFile(reader, htmlWidgetFilePath);
		JSonUtils.convertHtmlFileToJson(htmlWidgetFilePath, "index.html.js");
		String pageHtml = "";
		String pagePath = "templates/previewpage.html";
		is = ResourcesLoader.loadResource(pagePath);
		if (is == null) {
			trackingObject.updateStateAndMessage(States.aborted,
					"The conversion process failed because of a template handling problem : "
							+ pagePath);
			return;
		}
		try {
			pageHtml = IOUtils.toString(is, "UTF-8");
			String widgetHtml = FileUtils.readFileAsString(htmlWidgetFilePath);
			pageHtml = pageHtml.replace("WIDGET", widgetHtml);
		} catch (IOException e) {
			trackingObject.updateStateAndMessage(
					States.aborted,
					"Impossible to copye the preview template : "
							+ e.getMessage());
			e.printStackTrace();
			return;
		}
		String htmlPageFilePath = previewDirectoryPath + "/index.html";
		FileUtils.writeDataToFile(new StringReader(pageHtml), htmlPageFilePath);
		trackingObject.updateStateAndMessage(States.pending,
				"The web video player has been successfully created. The video file "
						+ entryPoint
						+ " is being converted to html5 compliant formats.");
		String videoFilePath = ResourceDirectoryInterface.getFilePath(
				resourceId, entryPoint);
		Set<String> outputs = new HashSet<String>();
		outputs.add("video/mp4");
		outputs.add("video/webm");
		outputs.add("video/ogg");
		List<String> images = ConversionServerInterface.askForConversion(
				videoFilePath, outputs);
		if (images == null) {
			// TODO enregistrer en base l'absence de preview
			String message = "No preview image obtained from conversion server interface for video file "
					+ entryPoint + " resource" + resourceId;
			logger.error(message);
			trackingObject.updateStateAndMessage(States.aborted, message);
			return;
		}
		trackingObject.updateStateAndMessage(States.pending,
				"The video file has been successfully converted.");
		for (int i = 0; i < images.size(); i++) {
			writePreviewFileToDisk(images.get(i), "output");
			trackingObject.updateStateAndMessage(States.pending, "The file "
					+ images.get(i) + "has been successfully copied to disk.");
		}
		trackingObject.updateStateAndMessage(States.done,
				"The video file has been successfully converted .");

	}
}
