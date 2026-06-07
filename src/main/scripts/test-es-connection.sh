export  your_password=changeme123
# Check cluster health
curl -u elastic:$your_password http://localhost:9200/_cluster/health?pretty

# List all indices
curl -u elastic:$your_password http://localhost:9200/_cat/indices?v

# Check if a specific index exists
curl -u elastic:$your_password -I http://localhost:9200/your-index-name

# Check nodes
curl -u elastic:$your_password http://localhost:9200/_cat/nodes?v
