
using Microsoft.VisualStudio.TestTools.UnitTesting;
using NetTopologySuite.Geometries;
using System.Linq;
using PointInPolygonTest.Parser.GeoJson;


namespace PointInPolygonTest.Tests;

[TestClass]
public class GeoJsonParserTests
{
    [TestMethod]
    public void ParsePolygons_Should_Parse_Single_Polygon()
    {
        var geoJson = @"{
                ""type"": ""FeatureCollection"",
                ""features"": [
                    {
                        ""type"": ""Feature"",
                        ""geometry"": {
                            ""type"": ""Polygon"",
                            ""coordinates"": [
                                [[0,0],[0,1],[1,1],[1,0],[0,0]]
                            ]
                        }
                    }
                ]
            }";

        var (polygons, points) = GeoJsonParser.ParsePolygons(geoJson);

        Assert.AreEqual(1, polygons.Count);
        Assert.AreEqual(0, points.Count);
        Assert.IsInstanceOfType(polygons.First(), typeof(Polygon));
    }

    [TestMethod]
    public void ParsePolygons_Should_Parse_Point()
    {
        var geoJson = @"{
                ""type"": ""FeatureCollection"",
                ""features"": [
                    {
                        ""type"": ""Feature"",
                        ""geometry"": {
                            ""type"": ""Point"",
                            ""coordinates"": [10, 20]
                        }
                    }
                ]
            }";

        var (polygons, points) = GeoJsonParser.ParsePolygons(geoJson);

        Assert.AreEqual(0, polygons.Count);
        Assert.AreEqual(1, points.Count);
        Assert.IsInstanceOfType(points.First(), typeof(Point));
    }

    [TestMethod]
    public void ParsePolygons_Should_Parse_MultiPolygon()
    {
        var geoJson = @"{
                ""type"": ""FeatureCollection"",
                ""features"": [
                    {
                        ""type"": ""Feature"",
                        ""geometry"": {
                            ""type"": ""MultiPolygon"",
                            ""coordinates"": [
                                [[[0,0],[0,1],[1,1],[1,0],[0,0]]],
                                [[[2,2],[2,3],[3,3],[3,2],[2,2]]]
                            ]
                        }
                    }
                ]
            }";

        var (polygons, points) = GeoJsonParser.ParsePolygons(geoJson);

        Assert.AreEqual(2, polygons.Count);
        Assert.AreEqual(0, points.Count);
    }

    [TestMethod]
    public void ParsePolygons_Should_Parse_Mixed_Geometries()
    {
        var geoJson = @"{
                ""type"": ""FeatureCollection"",
                ""features"": [
                    {
                        ""type"": ""Feature"",
                        ""geometry"": {
                            ""type"": ""Polygon"",
                            ""coordinates"": [
                                [[0,0],[0,1],[1,1],[1,0],[0,0]]
                            ]
                        }
                    },
                    {
                        ""type"": ""Feature"",
                        ""geometry"": {
                            ""type"": ""Point"",
                            ""coordinates"": [5, 5]
                        }
                    }
                ]
            }";

        var (polygons, points) = GeoJsonParser.ParsePolygons(geoJson);

        Assert.AreEqual(1, polygons.Count);
        Assert.AreEqual(1, points.Count);
    }

    [TestMethod]
    public void ParsePolygons_Should_Throw_On_Unknown_Type()
    {
        var geoJson = @"{
            ""type"": ""UnknownType""
        }";

        var exception = Assert.ThrowsException<NotImplementedException>(() =>
        {
            GeoJsonParser.ParsePolygons(geoJson);
        });

        Assert.AreEqual("The type UnknownType is not implemented.", exception.Message);
    }

    [TestMethod]
    public void ParsePolygons_Should_Handle_Empty_Input()
    {
        var geoJson = "{}";

        var (polygons, points) = GeoJsonParser.ParsePolygons(geoJson);

        Assert.AreEqual(0, polygons.Count);
        Assert.AreEqual(0, points.Count);
    }
}
