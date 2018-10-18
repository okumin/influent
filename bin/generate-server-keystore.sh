#!/bin/bash -eu

mkdir -p out
chmod 700 out

echo -n "Enter the certificate filename of CA [out/ca_cert.pem]:"
read ca_cert_filename
if [ -z "${ca_cert_filename}" ]; then
  ca_cert_filename="out/ca_cert.pem"
fi

echo -n "Enter the private key filename of CA [out/ca_key.pem]:"
read ca_key_filename
if [ -z "${ca_key_filename}" ]; then
  ca_key_filename="out/ca_key.pem"
fi

while true;  do
  echo -n "Enter private key password of existent CA:"
  read -s ca_key_password
  if [ "${#ca_key_password}" -ge 4 ]; then
    echo
    break;
  else
    echo
    echo "Password must be at least 4 characters"
    continue
  fi
done

while true;  do
  echo -n "Enter key store password for server:"
  read -s key_store_password
  if [ "${#key_store_password}" -ge 6 ]; then
    echo
    break;
  else
    echo
    echo "Password must be at least 6 characters"
    continue
  fi
done

while true;  do
  echo -n "Enter private key password for server:"
  read -s key_password
  if [ "${#key_password}" -ge 6 ]; then
    echo
    break;
  else
    echo
    echo "Password must be at least 6 characters"
    continue
  fi
done

echo -n "Enter a name of the cert name of CA [influent-ca]:"
read ca_cert_name
if [ -z "${ca_cert_name}" ]; then
  ca_cert_name=influent-ca
fi

echo -n "Enter a name of the keypair name for server [influent-server]:"
read keypair_name
if [ -z "${keypair_name}" ]; then
  keypair_name=influent-server
fi

while true; do
  echo -n "Enter Country Name for server [US]:"
  read country_name
  if [ -z "${country_name}" ]; then
    country_name=US
    break;
  elif [ "${#country_name}" -eq 2 ]; then
    break;
  else
    echo "Country Name must be 2 characters"
    continue
  fi
done

echo -n "Enter State or Province Name for server [CA]:"
read state_name
if [ -z "${state_name}" ]; then
  state_name=CA
fi

echo -n "Enter Locality Name (eg, city) for server [Mountain View]:"
read locality_name
if [ -z "${locality_name}" ]; then
  locality_name="Mountain View"
fi

echo -n "Organization Name (eg, company) for server [Influent]:"
read organization_name
if [ -z "${organization_name}" ]; then
  organization_name="Influent"
fi

echo -n "Organizational Unit Name (eg, section) for server []:"
read organization_unit_name
if [ -z "${organization_unit_name}" ]; then
  organization_unit_name=""
fi

echo -n "Common Name (e.g. server host name):"
read common_name
if [ -z "${common_name}" ]; then
  common_name="Influent Server"
fi

echo -n "Certificate valid days [36500]:"
read validity_days
if [ -z "${validity_days}" ]; then
  validity_days=36500
fi

keytool \
  -genkeypair \
  -alias ${keypair_name} \
  -keyalg RSA \
  -keysize 4096 \
  -dname "CN=${common_name}, OU=${organization_unit_name}, O=${organization_name}, L=${locality_name}, ST=${state_name}, C=${country_name}" \
  -validity ${validity_days} \
  -keypass ${key_password} \
  -keystore out/influent-server.jks \
  -storepass  ${key_store_password}

chmod 600 out/influent-server.jks

keytool \
  -certreq \
  -alias ${keypair_name} \
  -file out/influent-server.csr \
  -keypass ${key_password} \
  -keystore out/influent-server.jks \
  -storepass ${key_store_password}

openssl \
  x509 \
  -req \
  -in out/influent-server.csr \
  -out out/influent-server.crt \
  -passin pass:${ca_key_password} \
  -days ${validity_days} \
  -CA ${ca_cert_filename} \
  -CAkey ${ca_key_filename} \
  -CAcreateserial

keytool \
  -import \
  -alias ${ca_cert_name} \
  -file ${ca_cert_filename} \
  -keystore out/influent-server.jks \
  -storepass ${key_store_password} << EOF
y
EOF

keytool \
  -import \
  -alias ${keypair_name} \
  -file out/influent-server.crt \
  -keypass ${key_password} \
  -keystore out/influent-server.jks \
  -storepass ${key_store_password}

rm out/*.srl
rm out/influent-server.csr
rm out/influent-server.crt

echo "out/influent-server.jks are generated."
echo "You can check influent-server.jks by 'keytool -v -list -keystore out/influent-server.jks'"
