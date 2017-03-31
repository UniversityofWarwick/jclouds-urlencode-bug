package uk.ac.warwick.urlencoding;

import com.google.common.io.ByteSource;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.*;
import static org.junit.Assert.*;

abstract class AbstractJCloudsURLEncodingTest {

    private final String containerName = "encode-test";
    private final ByteSource in = ByteSource.wrap("Test".getBytes(StandardCharsets.UTF_8));

    abstract BlobStoreContext getContext();

    @Before
    public void setup() throws Exception {
        if (!getContext().getBlobStore().containerExists(containerName))
            getContext().getBlobStore().createContainerInLocation(null, containerName);
    }

    @After
    public void tearDown() throws Exception {
        if (getContext().getBlobStore().containerExists(containerName)) {
            getContext().getBlobStore().deleteContainer(containerName);
        }
    }

    private void putBlob(ByteSource in, String key) throws Exception {
        BlobStore blobStore = getContext().getBlobStore();

        long size = in.size();
        Blob blob =
            blobStore.blobBuilder(key)
                .payload(in)
                .contentDisposition(key)
                .contentLength(size)
                .build();

        blobStore.putBlob(containerName, blob);
    }

    private void assertCanPutAndList(String key) throws Exception {
        try {
            putBlob(in, key);

            Blob blob = getContext().getBlobStore().getBlob(containerName, key);
            assertEquals(4L, blob.getMetadata().getSize().longValue());

            List<String> keys =
                getContext().getBlobStore().list(containerName, ListContainerOptions.Builder.prefix("Files/").recursive())
                    .stream().map(StorageMetadata::getName)
                    .collect(toList());

            assertEquals(Collections.singletonList(key), keys);
        } finally {
            /*
             * Make sure the blob is removed after the test has run, because if we don't do that, we fall foul of
             * Swift behaviour where you remove a container but the key listing is cached - so more tests fail
             * because they have the $. key
             */
            getContext().getBlobStore().removeBlob(containerName, key);
        }
    }

    @Test
    public void notEncodedNoSpaces() throws Exception {
        String key = "Files/OpenOffice.org3.3/openofficeorg1.cab";

        assertCanPutAndList(key);
    }

    @Test
    public void notEncodedWithSpaces() throws Exception {
        String key = "Files/OpenOffice.org 3.3 (en-GB) Installation Files/openofficeorg1.cab";

        assertCanPutAndList(key);
    }

    @Test
    public void encodedWithSpaces() throws Exception {
        String key = "Files/OpenOffice.org 3.3 %28en-GB%29 Installation Files/openofficeorg1.cab";

        assertCanPutAndList(key);
    }

    @Test
    public void notEncodedWithIllegalEncode() throws Exception {
        String key = "Files/OpenOffice.org 3.3 (en-GB) Installation Files/openofficeorg1%$.cab";

        assertCanPutAndList(key);
    }

    @Test
    public void encodedWithIllegalEncode() throws Exception {
        String key = "Files/OpenOffice.org 3.3 %28en-GB%29 Installation Files/openofficeorg1%25$.cab";

        assertCanPutAndList(key);
    }
}
