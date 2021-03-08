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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.wallride.autoconfigure.WallRideCacheConfiguration;
import org.wallride.domain.Blog;
import org.wallride.domain.GoogleAnalytics;
import org.wallride.exception.GoogleAnalyticsException;
import org.wallride.exception.ServiceException;
import org.wallride.model.GoogleAnalyticsUpdateRequest;
import org.wallride.repository.BlogRepository;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.SecurityUtils;
import com.google.api.services.analyticsreporting.v4.AnalyticsReporting;
import com.google.api.services.analyticsreporting.v4.AnalyticsReportingScopes;
import com.google.api.services.analyticsreporting.v4.model.DateRange;
import com.google.api.services.analyticsreporting.v4.model.Dimension;
import com.google.api.services.analyticsreporting.v4.model.GetReportsRequest;
import com.google.api.services.analyticsreporting.v4.model.GetReportsResponse;
import com.google.api.services.analyticsreporting.v4.model.Metric;
import com.google.api.services.analyticsreporting.v4.model.Report;
import com.google.api.services.analyticsreporting.v4.model.ReportRequest;

@Service
@Transactional(rollbackFor=Exception.class)
public class BlogService {

	@Resource
	private BlogRepository blogRepository;

	private static Logger logger = LoggerFactory.getLogger(BlogService.class);

	@CacheEvict(value = WallRideCacheConfiguration.BLOG_CACHE, allEntries = true)
	public GoogleAnalytics updateGoogleAnalytics(GoogleAnalyticsUpdateRequest request) {
		byte[] p12;
		try {
			p12 = request.getServiceAccountP12File().getBytes();
		} catch (IOException e) {
			throw new ServiceException(e);
		}

		try {
			PrivateKey privateKey = SecurityUtils.loadPrivateKeyFromKeyStore(
					SecurityUtils.getPkcs12KeyStore(), new ByteArrayInputStream(p12),
					"notasecret", "privatekey", "notasecret");

			HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
			JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

			// Build service account credential.
			Set<String> scopes = new HashSet<>();
			scopes.add(AnalyticsReportingScopes.ANALYTICS_READONLY);

			GoogleCredential credential = new GoogleCredential.Builder().setTransport(httpTransport)
					.setJsonFactory(jsonFactory)
					.setServiceAccountId(request.getServiceAccountId())
					.setServiceAccountScopes(scopes)
					.setServiceAccountPrivateKey(privateKey)
					.build();

			AnalyticsReporting analytics = new AnalyticsReporting.Builder(httpTransport, jsonFactory, credential)
					.setApplicationName("WallRide")
					.build();

			// Create the DateRange object.
		    DateRange dateRange = new DateRange();
		    dateRange.setStartDate("2005-01-01");
		    dateRange.setEndDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
		    
		    // Create the Metrics object.
		    Metric sessions = new Metric()
		        .setExpression("ga:pageviews")
		        .setAlias("pageviews");
			
			//Create the Dimensions object.
		    Dimension dimension = new Dimension()
		        .setName(String.format("ga:dimension%d", request.getCustomDimensionIndex()));
		    
			
			// Create the ReportRequest object.
		    ReportRequest reportRequest = new ReportRequest()
		        .setViewId(request.getProfileId())
		        .setDateRanges(Arrays.asList(dateRange))
		        .setDimensions(Arrays.asList(dimension))
		        .setMetrics(Arrays.asList(sessions))
		        .setPageSize(1);
		    		   
		    logger.info(reportRequest.toString());
		    ArrayList<ReportRequest> reportRequests = new ArrayList<ReportRequest>();
		    reportRequests.add(reportRequest);

		    // Create the GetReportsRequest object.
		    GetReportsRequest getReport = new GetReportsRequest()
		        .setReportRequests(reportRequests);
		    
			// Call the batchGet method.
			final GetReportsResponse reportResponse = analytics.reports().batchGet(getReport).execute();
			
			Report report = reportResponse.getReports().get(0);
			logger.debug("report data: {}", report.getData());
		} catch (GeneralSecurityException e) {
			logger.debug("report data Exception: {}", e);
			throw new GoogleAnalyticsException(e);
		} catch (IOException e) {
			throw new GoogleAnalyticsException(e);
		}

		GoogleAnalytics googleAnalytics = new GoogleAnalytics();
		googleAnalytics.setTrackingId(request.getTrackingId());
		googleAnalytics.setProfileId(request.getProfileId());
		googleAnalytics.setCustomDimensionIndex(request.getCustomDimensionIndex());
		googleAnalytics.setServiceAccountId(request.getServiceAccountId());
		googleAnalytics.setServiceAccountP12FileName(request.getServiceAccountP12File().getOriginalFilename());
		googleAnalytics.setServiceAccountP12FileContent(p12);

		Blog blog = blogRepository.findOneForUpdateById(request.getBlogId());
		blog.setGoogleAnalytics(googleAnalytics);

		blog = blogRepository.saveAndFlush(blog);
		return blog.getGoogleAnalytics();
	}

	@CacheEvict(value = WallRideCacheConfiguration.BLOG_CACHE, allEntries = true)
	public GoogleAnalytics deleteGoogleAnalytics(long blogId) {
		Blog blog = blogRepository.findOneForUpdateById(blogId);
		GoogleAnalytics googleAnalytics = blog.getGoogleAnalytics();
		blog.setGoogleAnalytics(null);
		blogRepository.saveAndFlush(blog);
		return googleAnalytics;
	}

	@Cacheable(value = WallRideCacheConfiguration.BLOG_CACHE)
	public Blog getBlogById(long id) {
		return blogRepository.findOneById(id);
	}
}
