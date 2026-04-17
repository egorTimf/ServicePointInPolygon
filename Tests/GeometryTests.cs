using Microsoft.VisualStudio.TestTools.UnitTesting;
using NetTopologySuite.Geometries;

namespace PointInPolygonTest.Geometry.Tests;

[TestClass]
public class PointInPolygonTests
{
    //проверка пограничных случаев на простом квадрате
    private readonly Polygon _square = new(new LinearRing([
        new Coordinate(0, 0),
        new Coordinate(10, 0),
        new Coordinate(10, 10),
        new Coordinate(0, 10),
        new Coordinate(0, 0)]
    ));

    [TestMethod]
    public void SimpleOutSquare()
    {
        var point = new Point(-5, 5);
        Assert.IsFalse(PointInPolygon.Contains(_square, point));
    }
    [TestMethod]
    public void SimpleInsideSquare()
    {
        var point = new Point(5, 5);
        Assert.IsTrue(PointInPolygon.Contains(_square,  point));
    }
    [TestMethod]
    public void NearEdgeInsideSquare()
    {
        var point = new Point(0.000000002, 5);
        Assert.IsTrue(PointInPolygon.Contains(_square,  point));
    }
    [TestMethod]
    public void NearVertexInsideSquare()
    {
        var point = new Point(2e-9, 2e-9);
        Assert.IsTrue(PointInPolygon.Contains(_square,  point));
    }
    [TestMethod]
    public void NearVertexOutsideSquare()
    {
        var point = new Point(-2e-9, -2e-9);
        Assert.IsFalse(PointInPolygon.Contains(_square,  point));
    }
    [TestMethod]
    public void OnVertexSquare()
    {
        var point = new Point(0, 0);
        Assert.IsTrue(PointInPolygon.Contains(_square,  point));
    }
    [TestMethod]
    public void OnEdgeSquare()
    {
        var point = new Point(10, 5);
        Assert.IsTrue(PointInPolygon.Contains(_square,  point));
    }
    
    private readonly Polygon _selfIntersectingSquare = new(new LinearRing([
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0),
            new Coordinate(10, 10),
            new Coordinate(2, 5),
            new Coordinate(0, 0)]
    ));
    [TestMethod]
    public void SimpleOutSelfIntersectedSquare()
    {
        var point = new Point(-5, 5);
        Assert.IsFalse(PointInPolygon.Contains(_selfIntersectingSquare, point));
    }
    [TestMethod]
    public void SimpleInSelfIntersectedSquare()
    {
        var point = new Point(5, 5);
        Assert.IsTrue(PointInPolygon.Contains(_selfIntersectingSquare, point));
    }
    [TestMethod]
    public void SimpleInSelfIntersectedSquare2()
    {
        var point = new Point(1, 5);
        Assert.IsTrue(PointInPolygon.Contains(_selfIntersectingSquare, point));
    }
    [TestMethod]
    public void SimpleInSelfIntersectedSquare3()
    {
        var point = new Point(3.5, 5);
        Assert.IsTrue(PointInPolygon.Contains(_selfIntersectingSquare, point));
    }
    
    private readonly Polygon _clockPolygon = new(new LinearRing([
            new Coordinate(0, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(10, 0),
            new Coordinate(0, 0)]
    ));
    
    [TestMethod]
    public void PointInClock()
    {
        var point = new Point(1, 1);
        Assert.IsTrue(PointInPolygon.Contains(_clockPolygon, point));
    }
    [TestMethod]
    public void PointOutClock()
    {
        var point = new Point(0, 5);
        Assert.IsFalse(PointInPolygon.Contains(_clockPolygon, point));
    }
    [TestMethod]
    public void PointOnIntersectClock()
    {
        var point = new Point(5.0000000001, 5.00000000001);
        Assert.IsTrue(PointInPolygon.Contains(_clockPolygon, point));
    }
    [TestMethod]
    public void PointOutIntersectClock()
    {
        var point = new Point(2.893434321233, 3);
        Assert.IsFalse(PointInPolygon.Contains(_clockPolygon, point));
    }
    // ========================= // POLYGON WITH HOLE // =========================
    private readonly Polygon _polygonWithHole = new(
        new LinearRing(new[]
        {
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0)
        }),
        new[]
        {
            new LinearRing(new[]
            {
                new Coordinate(3, 3),
                new Coordinate(7, 3),
                new Coordinate(7, 7),
                new Coordinate(3, 7),
                new Coordinate(3, 3)
            })
        }
    );

    [TestMethod]
    public void Hole_InsideOuter()
    {
        Assert.IsTrue(PointInPolygon.Contains(_polygonWithHole, new Point(1, 1)));
    }

    [TestMethod]
    public void Hole_InsideHole()
    {
        Assert.IsFalse(PointInPolygon.Contains(_polygonWithHole, new Point(5, 5)));
    }

    [TestMethod]
    public void Hole_OnHoleEdge()
    {
        Assert.IsTrue(PointInPolygon.Contains(_polygonWithHole, new Point(3, 5)));
    }

    [TestMethod]
    public void Hole_OnOuterEdge()
    {
        Assert.IsTrue(PointInPolygon.Contains(_polygonWithHole, new Point(0, 5)));
    }

    [TestMethod]
    public void Hole_BetweenHoleAndOuter()
    {
        Assert.IsTrue(PointInPolygon.Contains(_polygonWithHole, new Point(2.9, 5)));
    }

    [TestMethod]
    public void Hole_JustInsideHoleBoundary()
    {
        Assert.IsFalse(PointInPolygon.Contains(_polygonWithHole, new Point(3+1.5e-9, 5)));
    }
    
    private readonly Polygon _octagonWithHoles = new(
            new LinearRing(new[]
            {
                new Coordinate(2, 0),
                new Coordinate(8, 0),
                new Coordinate(10, 2),
                new Coordinate(10, 8),
                new Coordinate(8, 10),
                new Coordinate(2, 10),
                new Coordinate(0, 8),
                new Coordinate(0, 2),
                new Coordinate(2, 0)
            }),
            new[]
            {
                new LinearRing(new[]
                {
                    new Coordinate(3,3),
                    new Coordinate(4,3),
                    new Coordinate(4,4),
                    new Coordinate(3,4),
                    new Coordinate(3,3)
                }),
                new LinearRing(new[]
                {
                    new Coordinate(6,6),
                    new Coordinate(7,6),
                    new Coordinate(7,7),
                    new Coordinate(6,7),
                    new Coordinate(6,6)
                })
            }
        );

        [TestMethod]
        public void Octagon_Inside()
        {
            Assert.IsTrue(PointInPolygon.Contains(_octagonWithHoles, new Point(5, 2)));
        }

        [TestMethod]
        public void Octagon_InsideSecondArea()
        {
            Assert.IsTrue(PointInPolygon.Contains(_octagonWithHoles, new Point(2, 5)));
        }

        [TestMethod]
        public void Octagon_InsideHole1()
        {
            Assert.IsFalse(PointInPolygon.Contains(_octagonWithHoles, new Point(3.5, 3.5)));
        }

        [TestMethod]
        public void Octagon_InsideHole2()
        {
            Assert.IsFalse(PointInPolygon.Contains(_octagonWithHoles, new Point(6.5, 6.5)));
        }

        [TestMethod]
        public void Octagon_BetweenHoles()
        {
            Assert.IsTrue(PointInPolygon.Contains(_octagonWithHoles, new Point(5, 5)));
        }

        [TestMethod]
        public void Octagon_Outside()
        {
            Assert.IsFalse(PointInPolygon.Contains(_octagonWithHoles, new Point(-1, 5)));
        }

        [TestMethod]
        public void Octagon_OnEdge()
        {
            Assert.IsTrue(PointInPolygon.Contains(_octagonWithHoles, new Point(2, 0)));
        }

        [TestMethod]
        public void Octagon_NearHoleBoundary()
        {
            Assert.IsFalse(PointInPolygon.Contains(_octagonWithHoles, new Point(4-2e-9, 3.5)));
        }
    }