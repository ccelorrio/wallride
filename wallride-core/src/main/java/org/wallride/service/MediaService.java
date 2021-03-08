/*
 * Copyright 2014 Tagbangers, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wallride.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.wallride.autoconfigure.WallRideCacheConfiguration;
import org.wallride.autoconfigure.WallRideProperties;
import org.wallride.domain.Media;
import org.wallride.repository.MediaRepository;
import org.wallride.support.ExtendedResourceUtils;

import com.mortennobel.imagescaling.AdvancedResizeOp;
import com.mortennobel.imagescaling.DimensionConstrain;
import com.mortennobel.imagescaling.ResampleFilters;
import com.mortennobel.imagescaling.ResampleOp;

@Service
@Transactional(rollbackFor=Exception.class)
public class MediaService {
	
	private static Logger logger = LoggerFactory.getLogger(MediaService.class);

	@Autowired
	private ResourceLoader resourceLoader;

	@Autowired
	private WallRideProperties wallRideProperties;

	@Autowired
	private MediaRepository mediaRepository;

	public Media createMedia(MultipartFile file) {
		Media media = new Media();
		media.setMimeType(file.getContentType());
		media.setOriginalName(file.getOriginalFilename());
		media = mediaRepository.saveAndFlush(media);

//		Blog blog = blogService.getBlogById(Blog.DEFAULT_ID);
		try {
			Resource prefix = resourceLoader.getResource(wallRideProperties.getMediaLocation());
			Resource resource = prefix.createRelative(media.getId());
//			AmazonS3ResourceUtils.writeMultipartFile(file, resource);
			ExtendedResourceUtils.write(resource, file);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}

		return media;
	}

	public List<Media> getAllMedias() {
		return mediaRepository.findAll( Sort.by(new Sort.Order(Sort.Direction.DESC, "createdAt")));
	}

	@Cacheable(value = WallRideCacheConfiguration.MEDIA_CACHE)
	public Media getMedia(String id) {
		return mediaRepository.findOneById(id);
	}

	
	
	public String removeUnusuedMedia(boolean doResize) {

		int filesRemoved = 0;
		long bytesRemoved = 0;
		int filesInMedia = 0;
		long bytesInMedia = 0;
		int bigFiles = 0;
		int bigFilesResized = 0;
		
		// Find all Media in DB
		Map<String,Media> dbMedia = new HashMap<String,Media>();
		List<Media> allMedias = getAllMedias();
		for(Media media : allMedias) {
			dbMedia.put(media.getId(), media);
		}
		logger.debug("Media in DB Total: " + dbMedia.size() + " files.");
		
		try {
			Resource prefix = resourceLoader.getResource(wallRideProperties.getMediaLocation());
			Resource [] mediaResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(prefix.getURL()+ "/*");

			// Loop all files in mediaFolder
			for(Resource mediaResource : mediaResources) {
				File file = mediaResource.getFile();
				logger.debug("CHECKING file " + file.getName() + " ...");
				// Check file is in Media
				if(dbMedia.containsKey(file.getName())) {
					long fileSize = file.length();
					logger.debug("OK file " + file.getName() + " (" + formatSize(fileSize) + ") is in DB.");
					filesInMedia++;
					bytesInMedia += fileSize;
					if(fileSize > 600 * 1024) {
						logger.warn("FILE " + file.getName() + " (" + formatSize(fileSize) + ") is BIGGER THAN 600KB.");
						bigFiles++;
						
						if (doResize && "image".equals(MediaType.parseMediaType(dbMedia.get(file.getName()).getMimeType()).getType())) {
							File temp = File.createTempFile(
									getClass().getCanonicalName() + ".resized-",
									"." + MediaType.parseMediaType(dbMedia.get(file.getName()).getMimeType()).getSubtype());
							temp.deleteOnExit();
							resizeImage(file, temp, 2000, 2000, Media.ResizeMode.RESIZE);

							FileUtils.copyFile(temp, file);
							FileUtils.deleteQuietly(temp);
							bigFilesResized++;
						}
						
					}
				}
				else {
					// File not in MEDIA, so DELETE it
					long fileSize = file.length();
					if (file.delete()) { 
						logger.debug("KO file " + file.getName() + " is NOT in DB.");
						filesRemoved++;
						bytesRemoved += fileSize;
				    } else {
				      logger.error("ERROR Trying to delete file: " + file.getName());
				    } 					
				}
			}
			
		}
		catch (IOException e) {
			return e.getMessage();
		}
				
		logger.debug("TOTAL " + filesRemoved + " files removed (" + formatSize(bytesRemoved) + " freed).");
		logger.debug("TOTAL " + filesInMedia + " files in MEDIA (" + formatSize(bytesInMedia) + ").");
		logger.warn("TOTAL " + bigFiles + " files bigger that 600KB (" + bigFilesResized + " resized).");
		return "OK " + filesRemoved + " files removed (" +  formatSize(bytesRemoved) + " freed).  " 
			+ filesInMedia + " files in MEDIA (" + formatSize(bytesInMedia) + ").  "
					+ "TOTAL " + bigFiles + " files bigger that 600KB (" + bigFilesResized + " resized).";
		
	}
	
	private String formatSize(long v) {
	    if (v < 1024) return v + " B";
	    int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
	    return String.format("%.1f %sB", (double)v / (1L << (z*10)), " KMGTPE".charAt(z));
	}
	
	private void resizeImage(File inputImage, File file, int width, int height, Media.ResizeMode mode) throws IOException {
		long startTime = System.currentTimeMillis();

		if (width <= 0) {
			width = Integer.MAX_VALUE;
		}
		if (height <= 0) {
			height = Integer.MAX_VALUE;
		}

		BufferedImage image = ImageIO.read( new FileInputStream(inputImage));

		ResampleOp resampleOp;
		BufferedImage resized;

		switch (mode) {
			case RESIZE:
				resampleOp = new ResampleOp(DimensionConstrain.createMaxDimension(width, height, true));
				resampleOp.setFilter(ResampleFilters.getLanczos3Filter());
				resampleOp.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.Normal);
				resized = resampleOp.filter(image, null);
				ImageIO.write(resized, StringUtils.getFilenameExtension(file.getName()), file);
				break;
			case CROP:
				float wr = (float) width / (float) image.getWidth();
				float hr = (float) height / (float) image.getHeight();
				float fraction = (wr > hr) ? wr : hr;

				if (fraction < 1) {
					resampleOp = new ResampleOp(DimensionConstrain.createRelativeDimension(fraction));
					resampleOp.setFilter(ResampleFilters.getLanczos3Filter());
					resampleOp.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.Normal);
					resized = resampleOp.filter(image, null);
				} else {
					resized = image;
				}

				if (resized.getWidth() > width) {
					resized = resized.getSubimage((resized.getWidth() - width) / 2, 0, width, resized.getHeight());
				} else if (resized.getHeight() > height) {
					resized = resized.getSubimage(0, (resized.getHeight() - height) / 2, resized.getWidth(), height);
				}

				ImageIO.write(resized, StringUtils.getFilenameExtension(file.getName()), file);
				break;
			default:
				throw new IllegalStateException();
		}

		long stopTime = System.currentTimeMillis();
		logger.debug("Resized image: time [{}ms]", stopTime - startTime);
	}
}
