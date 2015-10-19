package com.hp.octane.plugins.jenkins.events;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.hp.octane.plugins.jenkins.ExtensionUtil;
import com.hp.octane.plugins.jenkins.actions.PluginActions;
import com.hp.octane.plugins.jenkins.model.events.CIEventType;
import hudson.model.FreeStyleProject;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * Created with IntelliJ IDEA.
 * User: gullery
 * Date: 13/01/15
 * Time: 22:05
 * To change this template use File | Settings | File Templates.
 */

public class EventsTest {
	private static final Logger logger = Logger.getLogger(EventsTest.class.getName());

	static final private String projectName = "root-job-events-case";
	static final private int DEFAULT_TESTING_SERVER_PORT = 9999;
	static final private String sharedSpaceId = "1007";
	static final private String username = "some";
	static final private String password = "pass";

	static private Server server;
	static private int testingServerPort = DEFAULT_TESTING_SERVER_PORT;
	static private EventsHandler eventsHandler;

	@ClassRule
	static public final JenkinsRule rule = new JenkinsRule();

	private static final class EventsHandler extends AbstractHandler {
		private final List<JSONObject> eventLists = new ArrayList<JSONObject>();

		@Override
		public void handle(String s, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
			logger.info("EVENTS TEST: server mock requested: " + baseRequest.getMethod() + " " + baseRequest.getPathInfo());

			String body = "";
			byte[] buffer;
			int len;
			//  TODO: fix those when auth is back in game
			if (request.getPathInfo().equals("/qcbin/authentication-point/alm-authenticate")) {
				response.setStatus(HttpServletResponse.SC_OK);
			} else if (request.getPathInfo().equals("/qcbin/rest/site-session")) {
				response.setStatus(HttpServletResponse.SC_CREATED);
			} else if (request.getPathInfo().equals("/internal-api/shared_spaces/" + sharedSpaceId + "/analytics/ci/events")) {
				buffer = new byte[1024];
				while ((len = request.getInputStream().read(buffer, 0, 1024)) > 0) {
					body += new String(buffer, 0, len);
				}
				eventLists.add(new JSONObject(body));
				response.setStatus(HttpServletResponse.SC_OK);
			}
			baseRequest.setHandled(true);
		}

		public List<JSONObject> getResults() {
			return eventLists;
		}

		public void clearResults() {
			eventLists.clear();
		}
	}

	public EventsTest() {
		String p = System.getProperty("testingServerPort");
		try {
			if (p != null) testingServerPort = Integer.parseInt(p);
		} catch (NumberFormatException nfe) {
			logger.info("EVENTS TEST: bad port number format, default port will be used: " + testingServerPort);
		}
	}

	static private void configEventsClient(JenkinsRule.WebClient client) throws Exception {
		WebRequestSettings req = new WebRequestSettings(client.createCrumbedUrl("octane/configuration/save"), HttpMethod.POST);
		JSONObject json = new JSONObject();
		json.put("location", "http://localhost:" + testingServerPort);
		json.put("sharedSpace", sharedSpaceId);
		json.put("username", username);
		json.put("password", password);
		req.setRequestBody(json.toString());
		logger.info("EVENTS TEST: submitting configuration to '" + client.getContextPath() + "'...");
		WebResponse res = client.loadWebResponse(req);
		logger.info("EVENTS TEST: configuration submitted and responded with result: " + res.getStatusMessage() + "; testing server will run on port " + testingServerPort);

		req = new WebRequestSettings(client.createCrumbedUrl("octane/status"), HttpMethod.GET);
		res = client.loadWebResponse(req);
		//  TODO: add validation on plugin status data
		logger.info("EVENTS TEST: plugin status of '" + client.getContextPath() + "': " + res.getContentAsString());
	}

	@BeforeClass
	static public void beforeClass() throws Exception {
		eventsHandler = new EventsHandler();
		server = new Server(testingServerPort);
		server.setHandler(eventsHandler);
		server.start();
	}

	@AfterClass
	static public void afterClass() throws Exception {
		server.stop();
		server.destroy();
	}

	@Test
	public void testEventsA() throws Exception {
		FreeStyleProject p = rule.createFreeStyleProject(projectName);
		JenkinsRule.WebClient client = rule.createWebClient();

		configEventsClient(client);

		EventsService eventsService = ExtensionUtil.getInstance(rule, EventsService.class);
		assertEquals(1, eventsService.getStatus().size());
		assertEquals("http://localhost:" + testingServerPort, eventsService.getStatus().get(0).getLocation());
		assertEquals(1, rule.jenkins.getTopLevelItemNames().size());
		assertTrue(rule.jenkins.getTopLevelItemNames().contains(projectName));

		assertEquals(0, p.getBuilds().toArray().length);
		p.scheduleBuild(0, null);
		while (p.getLastBuild() == null || p.getLastBuild().isBuilding()) {
			Thread.sleep(1000);
		}
		assertEquals(1, p.getBuilds().toArray().length);
		Thread.sleep(5000);

		List<CIEventType> eventsOrder = new ArrayList<CIEventType>(Arrays.asList(CIEventType.STARTED, CIEventType.FINISHED));
		List<JSONObject> eventLists = eventsHandler.getResults();
		JSONObject tmp;
		JSONArray events;
		logger.info("EVENTS TEST: server mock received " + eventLists.size() + " events");
		for (JSONObject l : eventLists) {
			assertEquals(2, l.length());

			assertFalse(l.isNull("server"));
			tmp = l.getJSONObject("server");
			assertEquals(new PluginActions.ServerInfo().getUrl(), tmp.getString("url"));
			assertEquals(new PluginActions.ServerInfo().getType(), tmp.getString("type"));
			assertEquals(new PluginActions.ServerInfo().getInstanceId(), tmp.getString("instanceId"));

			assertFalse(l.isNull("events"));
			events = l.getJSONArray("events");
			for (int i = 0; i < events.length(); i++) {
				tmp = events.getJSONObject(i);
				if (tmp.getString("project").equals("root-job")) {
					assertEquals(eventsOrder.get(0), CIEventType.getByValue(tmp.getString("eventType")));
					eventsOrder.remove(0);
				}
			}
		}
		//assertEquals(0, eventsOrder.size());
	}
}
