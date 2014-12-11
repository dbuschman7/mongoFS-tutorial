#!/bin/sh
set -x

. ./host.env

curl 	--request PUT \
	--header "Content-Type: multipart/form-data" \
	--header "Accept: application/json"\
	-w "\\nHTTP Response : %{http_code}\\n" \
	-F "data=@/Users/dbusch/dev/dbuschman7/mongoFS-tutorial/src/main/resources/image1.jpg" \
	${HOST}/mongofs/upload	

