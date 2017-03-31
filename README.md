# JClouds object listing bug with objects with invalid escape sequences

This repository demonstrates an apparent bug in JClouds when listing objects from a container where an object's name has
an invalid escape sequence in - when JClouds tries to URL-decode the path returned, it throws an exception from the stock
Java URLDecoder.

## Steps to reproduce

### Run a Swift installation (e.g. with Devstack)

We used [a Vagrant devstack install](https://github.com/openstack-dev/devstack-vagrant) with the default config. We had
to make one change due to the latest Devstack not supporting Ubuntu trusty (at the time of writing) to set FORCE=true in
the Vagrantfile before provisioning.

    diff --git a/Vagrantfile b/Vagrantfile
    index 1806543..6b102cd 100644
    --- a/Vagrantfile
    +++ b/Vagrantfile
    @@ -78,7 +78,7 @@ def configure_vm(name, vm, conf)

       if conf['setup_mode'] == "devstack"
         vm.provision "shell" do |shell|
    -      shell.inline = "sudo su - stack -c 'cd ~/devstack && ./stack.sh'"
    +      shell.inline = "sudo su - stack -c 'cd ~/devstack && FORCE=yes ./stack.sh'"
         end
       end

Our sample config.yaml (setting passwords and enabling Swift at the bottom):

    hostname_manager: manager.yoursite.com
    hostname_compute: compute.yoursite.com

    user_domains: .yoursite.com

    stack_password: secretadmin
    service_password: secretadmin
    admin_password: secretadmin

    stack_sshkey:

    setup_mode: devstack

    bridge_int: eth1

    manager_extra_services: s-proxy s-object s-container s-account

### Set Swift authentication properties

You can edit `src/test/resources/swift.properties` to put the correct credentials in before running the tests.

### Run the tests

Run `./gradlew test`. This will run the tests for a transient, openstack-swift and filesystem blob store - you should
see that the only test that fails is `SwiftJCloudsURLEncodingTest.notEncodedWithIllegalEncode` - this will fail after the
object has been successfully uploaded when trying to list keys from the container it was uploaded to.

## Sample output

This test uses a key of `Files/OpenOffice.org 3.3 (en-GB) Installation Files/openofficeorg1%$.cab`

    1089 DEBUG jclouds.headers >> PUT http://137.205.194.8:8080/v1/AUTH_d92af3a8d7f44a1091fe38f863ebb69e/encode-test/Files/OpenOffice.org%203.3%20%28en-GB%29%20Installation%20Files/openofficeorg1%25%24.cab HTTP/1.1
    ...
    1089 DEBUG jclouds.headers >> Content-Type: application/unknown
    1090 DEBUG jclouds.headers >> Content-Length: 4
    1090 DEBUG jclouds.headers >> Content-Disposition: Files/OpenOffice.org 3.3 (en-GB) Installation Files/openofficeorg1%$.cab
    1104 DEBUG jclouds.headers << HTTP/1.1 201 Created
    ...
    1172 DEBUG jclouds.headers >> GET http://137.205.194.8:8080/v1/AUTH_d92af3a8d7f44a1091fe38f863ebb69e/encode-test?format=json&prefix=Files/ HTTP/1.1
    ...
    1182 DEBUG jclouds.headers << HTTP/1.1 200 OK
    ...
    1182 DEBUG jclouds.headers << Content-Type: application/json; charset=utf-8
    1182 DEBUG jclouds.headers << Content-Length: 228
    1183 DEBUG jclouds.wire << "[{"hash": "0cbc6611f5540bd0809a388dc95a615b", "last_modified": "2017-03-31T09:34:41.522970", "bytes": 4, "name": "Files/OpenOffice.org 3.3 (en-GB) Installation Files/openofficeorg1%$.cab", "content_type": "application/unknown"}]"

    java.lang.IllegalArgumentException: URLDecoder: Illegal hex characters in escape (%) pattern - For input string: "$."

        at java.net.URLDecoder.decode(URLDecoder.java:194)
        at org.jclouds.util.Strings2.urlDecode(Strings2.java:131)
        at org.jclouds.http.Uris$UriBuilder.path(Uris.java:143)
        at org.jclouds.http.Uris$UriBuilder.appendPath(Uris.java:151)
        at org.jclouds.openstack.swift.v1.functions.ParseObjectListFromResponse$ToSwiftObject.apply(ParseObjectListFromResponse.java:99)
        at org.jclouds.openstack.swift.v1.functions.ParseObjectListFromResponse$ToSwiftObject.apply(ParseObjectListFromResponse.java:78)
        at com.google.common.collect.Lists$TransformingRandomAccessList$1.transform(Lists.java:582)
        at com.google.common.collect.TransformedIterator.next(TransformedIterator.java:48)
        at com.google.common.collect.TransformedIterator.next(TransformedIterator.java:48)
        at java.util.AbstractCollection.addAll(AbstractCollection.java:343)
        at com.google.common.collect.Iterables.addAll(Iterables.java:348)
        at org.jclouds.blobstore.domain.internal.PageSetImpl.<init>(PageSetImpl.java:31)
        at org.jclouds.openstack.swift.v1.blobstore.RegionScopedSwiftBlobStore.list(RegionScopedSwiftBlobStore.java:254)
        at uk.ac.warwick.urlencoding.AbstractJCloudsURLEncodingTest.assertCanPutAndList(AbstractJCloudsURLEncodingTest.java:62)
        at uk.ac.warwick.urlencoding.AbstractJCloudsURLEncodingTest.notEncodedWithIllegalEncode(AbstractJCloudsURLEncodingTest.java:102)
        at uk.ac.warwick.urlencoding.SwiftJCloudsURLEncodingTest.notEncodedWithIllegalEncode(SwiftJCloudsURLEncodingTest.java:7)
        ...

The bug here, as I see it, is that `ParseObjectListFromResponse$ToSwiftObject` appends a path to a `UriBuilder` that has
already been decoded by the time it's returned to the client, so `UriBuilder.path` effectively double-decodes the response
when building the keys.

## Behaviour in `python-swiftclient`

This is the reference implementation of a Swift client, and correctly lists the key despite the invalid escape sequence.

    $ sudo pip install python-swiftclient python-keystoneclient
    ...
    $ echo "Test" > upload.txt

    $ swift \
        --os-auth-url http://137.205.194.8:5000/identity/v2.0 \
        --os-tenant-name demo \
        --os-username demo \
        --os-password secretadmin \
        upload \
        --object-name 'Files/OpenOffice.org 3.3 (en-GB) Installation Files/openofficeorg1%$.cab' \
        encode-test upload.txt
    Files/OpenOffice.org 3.3 (en-GB) Installation Files/openofficeorg1%$.cab

    $ swift \
        --os-auth-url http://137.205.194.8:5000/identity/v2.0 \
        --os-tenant-name demo \
        --os-username demo \
        --os-password secretadmin \
        list encode-test
    Files/OpenOffice.org 3.3 (en-GB) Installation Files/openofficeorg1%$.cab
