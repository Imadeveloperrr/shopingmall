#!/usr/bin/env python3
"""
Simple script to generate embedding and create SQL update statement
"""
import requests
import json

# Test the API endpoint to get embedding
url = "http://localhost:8080/api/test/recommendation/embedding"
payload = {
    "text": "Soft knit material warm winter knitwear Essential winter item feel the warmth of knit"
}

try:
    response = requests.post(url,
                           json=payload,
                           headers={'Content-Type': 'application/json'},
                           timeout=10)

    if response.status_code == 200:
        data = response.json()
        print("✅ Embedding API call successful")
        print(f"Dimension: {data['embeddingDimension']}")
        print(f"Processing time: {data['processingTimeMs']}ms")
        print(f"First 5 values: {data['embeddingPreview']}")

        # The actual embedding would need to be retrieved from the full response
        # For now, we'll create a mock embedding for testing
        print("\n🔧 Creating SQL update statement...")
        print("Note: This is a simplified embedding for testing purposes")

        # Create a simple test vector (normally would use the full 1536-dimensional vector)
        test_embedding = [0.1] * 1536  # Simple test vector
        vector_str = "[" + ",".join([str(x) for x in test_embedding]) + "]"

        sql = f"""
        UPDATE product
        SET description_vector = '{vector_str}'::vector
        WHERE number = 3;
        """

        print(f"SQL Update Statement:")
        print(sql)

    else:
        print(f"❌ API call failed: {response.status_code}")
        print(f"Response: {response.text}")

except Exception as e:
    print(f"❌ Error: {e}")