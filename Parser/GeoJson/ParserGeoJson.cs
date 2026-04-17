using NetTopologySuite.Features;
using NetTopologySuite.Geometries;
using NetTopologySuite.IO;
using System.Text.Json;

namespace PointInPolygonTest.Geometry
{
    public static class GeoJsonParser
    {
        public static List<Polygon> ParsePolygons(string geoJson)
        {
            var reader = new GeoJsonReader();
            var result = new List<Polygon>();

            var obj = JsonSerializer.Deserialize<object>(geoJson);

            switch (obj)
            {
                case JsonElement element:
                    ParseElement(element, reader, result);
                    break;
            }

            return result;
        }

        private static void ParseElement(JsonElement element, GeoJsonReader reader, List<Polygon> result)
        {
            if (!element.TryGetProperty("type", out var typeProp))
                return;

            var type = typeProp.GetString();

            switch (type)
            {
                case "FeatureCollection":
                    foreach (var feature in element.GetProperty("features").EnumerateArray())
                    {
                        ParseElement(feature, reader, result);
                    }
                    break;

                case "Feature":
                    ParseElement(element.GetProperty("geometry"), reader, result);
                    break;

                case "Polygon":
                    var polygon = reader.Read<Polygon>(element.GetRawText());
                    result.Add(polygon);
                    break;

                case "MultiPolygon":
                    var multi = reader.Read<MultiPolygon>(element.GetRawText());
                    result.AddRange(multi.Geometries.Cast<Polygon>());
                    break;
            }
        }
    }
}