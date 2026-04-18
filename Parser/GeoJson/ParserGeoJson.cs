using System.Text.Json;
using NetTopologySuite.Geometries;
using NetTopologySuite.IO;

namespace PointInPolygonTest.Parser.GeoJson;

public static class GeoJsonParser
{
    public static (List<Polygon>, List<Point>) ParsePolygons(string geoJson)
    {
        var reader = new GeoJsonReader();
        var polygons = new List<Polygon>();
        var points = new List<Point>();
        
        var obj = JsonSerializer.Deserialize<object>(geoJson);

        switch (obj)
        {
            case JsonElement element:
                ParseElement(element, reader, polygons, points);
                break;
        }

        return (polygons, points);
    }

    private static void ParseElement(JsonElement element, GeoJsonReader reader, List<Polygon> polygons, List<Point> points)
    {
        while (true)
        {
            if (!element.TryGetProperty("type", out var typeProp)) return;

            var type = typeProp.GetString();

            switch (type)
            {
                case "FeatureCollection":
                    var features = element.GetProperty("features").EnumerateArray();
                    foreach (var feature in features) ParseElement(feature, reader, polygons, points);
                    break;

                case "Feature":
                    element = element.GetProperty("geometry");
                    continue;

                case "Polygon":
                    var polygon = reader.Read<Polygon>(element.GetRawText());
                    polygons.Add(polygon);
                    break;

                case "Point":
                    var point = reader.Read<Point>(element.GetRawText());
                    points.Add(point);
                    break;

                case "MultiPolygon":
                    var multi = reader.Read<MultiPolygon>(element.GetRawText());
                    polygons.AddRange(multi.Geometries.Cast<Polygon>());
                    break;
                default:
                    throw new NotImplementedException($"The type {type} is not implemented.");
            }

            break;
        }
    }
}