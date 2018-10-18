#!/bin/bash -eu

mkdir -p out
chmod 700 out

while true;  do
  echo -n "Enter private key password for CA:"
  read -s password
  if [ "${#password}" -ge 4 ]; then
    echo
    break;
  else
    echo
    echo "Password must be at least 4 characters"
    continue
  fi
done

while true; do
  echo -n "Enter Country Name for CA [US]:"
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

echo -n "Enter State or Province Name for CA [CA]:"
read state_name
if [ -z "${state_name}" ]; then
  state_name=CA
fi

echo -n "Enter Locality Name (eg, city) for CA [Mountain View]:"
read locality_name
if [ -z "${locality_name}" ]; then
  locality_name="Mountain View"
fi

echo -n "Organization Name (eg, company) for CA [Influent]:"
read organization_name
if [ -z "${organization_name}" ]; then
  organization_name="Influent"
fi

echo -n "Organizational Unit Name (eg, section) for CA []:"
read organization_unit_name
if [ -z "${organization_unit_name}" ]; then
  organization_unit_name=""
fi

echo -n "Common Name (e.g. server FQDN or YOUR name) [Influent CA]:"
read common_name
if [ -z "${common_name}" ]; then
  common_name="Influent CA"
fi

echo -n "Certificate valid days [36500]:"
read validity_days
if [ -z "${validity_days}" ]; then
  validity_days=36500
fi

openssl req \
  -new \
  -x509 \
  -newkey rsa:4096 \
  -out out/ca_cert.pem \
  -keyout out/ca_key.pem \
  -days ${validity_days} \
  -passout pass:${password} \
  -subj "/C=${country_name}/ST=${state_name}/L=${locality_name}/O=${organization_name}/OU=${organization_unit_name}/CN=${common_name}"

chmod 600 out/ca_key.pem

echo "out/ca_key.pem and out/ca_cert.pem are generated."
echo "You can check ca_key.pem by 'openssl rsa -text -in out/ca_key.pem'"
echo "You can check ca_cert.pem by 'openssl x509 -text -in out/ca_cert.pem'"
