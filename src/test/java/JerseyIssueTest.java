import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.fail;

public class JerseyIssueTest {
    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(9999);

    private Client client;

    private List<InputStream> responseInputStreams = new ArrayList<>();

    @BeforeClass
    public static void setupMockRestCall() throws Exception {
        MappingBuilder expectedQuery = get(urlPathEqualTo("/foo"));
        ResponseDefinitionBuilder expectedResponse = aResponse().withStatus(200).withBody(
            "Hello, World! " +
            "In production this response would be large, " +
            "and unsuitable for buffering in memory"
        );
        stubFor(expectedQuery.willReturn(expectedResponse));
    }

    @Before
    public void configureRestClient() {
        ClientResponseFilter trackInputStreams = new ClientResponseFilter() {
            @Override
            public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
                responseInputStreams.add(responseContext.getEntityStream());
            }
        };

        client = ClientBuilder.newClient().register(trackInputStreams);
    }

    @Test
    public void thisShouldWorkButFails() throws Exception {
        InputStream stream = client.target("http://localhost:9999/foo").request().get(InputStream.class);
        try {
            while (stream.read() != -1) {
                //consume the stream fully
            }
        } finally {
            stream.close();
        }

        assertThatAllInputStreamsAreClosed();
    }

    @Test
    public void thisWorksButIsReallyUgly() throws Exception {
        Response response = client.target("http://localhost:9999/foo").request().get();
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            throw new RuntimeException("We have to manually check that the response was successful");
        }
        InputStream stream = response.readEntity(InputStream.class);
        try {
            while (stream.read() != -1) {
                //consume the stream fully
            }
        } finally {
            response.close();
        }
    }

    @Test
    public void thisAlsoFails() throws Exception {
        Response response = client.target("http://localhost:9999/foo").request().get();
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            throw new RuntimeException("We have to manually check that the response was successful");
        }

        InputStream stream = response.readEntity(InputStream.class);
        try {
            while (stream.read() != -1) {
                //consume the stream fully
            }
        } finally {
            stream.close();
        }

        assertThatAllInputStreamsAreClosed();
    }

    @Test
    public void worksWithACast_ifYouKnowThatYouCanCast() throws Exception {
        Response response = client.target("http://localhost:9999/foo").request().get();
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            throw new RuntimeException("We have to manually check that the response was successful");
        }

        InputStream stream = (InputStream) response.getEntity();
        try {
            while (stream.read() != -1) {
                //consume the stream fully
            }
        } finally {
            stream.close();
        }

        assertThatAllInputStreamsAreClosed();
    }

    private void assertThatAllInputStreamsAreClosed() {
        for (InputStream stream : responseInputStreams) {
            assertClosed(stream);
        }
    }

    private void assertClosed(InputStream stream) {
        try {
            //noinspection ResultOfMethodCallIgnored
            stream.read(); //it's not ignored — we're checking for the exception
            fail("stream is not closed");
        } catch (IOException e) {
            //it's closed like it should be
        }
    }
}
