package uk.ac.warwick.urlencoding;

import org.jclouds.blobstore.BlobStoreContext;
import org.junit.After;
import uk.ac.warwick.TestUtils;

public class SwiftJCloudsURLEncodingTest extends AbstractJCloudsURLEncodingTest {

    private final BlobStoreContext context = TestUtils.createSwiftBlobStoreContext();

    @Override
    BlobStoreContext getContext() {
        return context;
    }

    @After
    public void tearDown() throws Exception {
        context.close();
    }
}
