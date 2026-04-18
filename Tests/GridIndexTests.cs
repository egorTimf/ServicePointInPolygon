using Microsoft.VisualStudio.TestTools.UnitTesting;
using NetTopologySuite.Geometries;
using PointInPolygonTest.Indexing;

namespace PointInPolygonTest.Tests;

[TestClass]
public class GridIndexInsertTests
{
    private GeometryFactory _factory;

    [TestInitialize]
    public void Setup()
    {
        _factory = new GeometryFactory();
    }

    [TestMethod]
    public void Insert_SinglePolygon_ShouldBeInCorrectCells()
    {
        var grid = new GridIndex(cellSize: 10);

        var polygon = _factory.CreatePolygon(new[]
        {
            new Coordinate(5, 5),
            new Coordinate(15, 5),
            new Coordinate(15, 15),
            new Coordinate(5, 15),
            new Coordinate(5, 5)
        });

        grid.Insert(polygon);

        var dict = grid.GetGridDictionary();

        // Ожидаемые клетки
        Assert.IsTrue(dict.ContainsKey((0, 0)));
        Assert.IsTrue(dict.ContainsKey((1, 0)));
        Assert.IsTrue(dict.ContainsKey((0, 1)));
        Assert.IsTrue(dict.ContainsKey((1, 1)));

        Assert.IsTrue(dict[(0, 0)].Contains(polygon));
        Assert.IsTrue(dict[(1, 1)].Contains(polygon));
    }
}

[TestClass]
public class GridIndexMultipleInsertTests
{
    private GeometryFactory _factory;

    [TestInitialize]
    public void Setup()
    {
        _factory = new GeometryFactory();
    }

    [TestMethod]
    public void Insert_MultiplePolygons_SameCell_ShouldAllBeStored()
    {
        var grid = new GridIndex(cellSize: 10);

        var poly1 = _factory.CreatePolygon(new[]
        {
            new Coordinate(1, 1),
            new Coordinate(4, 1),
            new Coordinate(4, 4),
            new Coordinate(1, 4),
            new Coordinate(1, 1)
        });

        var poly2 = _factory.CreatePolygon(new[]
        {
            new Coordinate(2, 2),
            new Coordinate(6, 2),
            new Coordinate(6, 6),
            new Coordinate(2, 6),
            new Coordinate(2, 2)
        });

        grid.Insert(poly1);
        grid.Insert(poly2);

        var dict = grid.GetGridDictionary();

        Assert.IsTrue(dict.ContainsKey((0, 0)));

        var cell = dict[(0, 0)];

        Assert.AreEqual(2, cell.Count);
        Assert.IsTrue(cell.Contains(poly1));
        Assert.IsTrue(cell.Contains(poly2));
    }
}

[TestClass]
public class GridIndexContainsTests
{
    private GeometryFactory _factory;

    [TestInitialize]
    public void Setup()
    {
        _factory = new GeometryFactory();
    }

    [TestMethod]
    public void Contains_PointInsidePolygon_ShouldReturnTrue()
    {
        var grid = new GridIndex(cellSize: 10);

        var polygon = _factory.CreatePolygon(new[]
        {
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0)
        });

        grid.Insert(polygon);

        var point = new Point(5, 5);

        var result = grid.Contains(point);

        Assert.IsTrue(result);
    }

    [TestMethod]
    public void Contains_PointOutsidePolygon_ShouldReturnFalse()
    {
        var grid = new GridIndex(cellSize: 10);

        var polygon = _factory.CreatePolygon(new[]
        {
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0)
        });

        grid.Insert(polygon);

        var point = new Point(20, 20);

        var result = grid.Contains(point);

        Assert.IsFalse(result);
    }

    [TestMethod]
    public void Contains_PointInEmptyCell_ShouldReturnFalse()
    {
        var grid = new GridIndex(cellSize: 10);

        var point = new Point(100, 100);

        var result = grid.Contains(point);

        Assert.IsFalse(result);
    }
}

[TestClass]
public class GridIndexDuplicateTests
{
    private GeometryFactory _factory;

    [TestInitialize]
    public void Setup()
    {
        _factory = new GeometryFactory();
    }

    [TestMethod]
    public void Insert_SamePolygonTwice_ShouldNotDuplicateInCell()
    {
        var grid = new GridIndex(cellSize: 10);

        var polygon = _factory.CreatePolygon(new[]
        {
            new Coordinate(1, 1),
            new Coordinate(4, 1),
            new Coordinate(4, 4),
            new Coordinate(1, 4),
            new Coordinate(1, 1)
        });

        // Вставляем один и тот же объект дважды
        grid.Insert(polygon);
        grid.Insert(polygon);

        var dict = grid.GetGridDictionary();

        Assert.IsTrue(dict.ContainsKey((0, 0)));

        var cell = dict[(0, 0)];

        // Должен быть только один
        Assert.AreEqual(1, cell.Count);
        Assert.IsTrue(cell.Contains(polygon));
    }
    [TestMethod]
    public void Insert_EqualGeometryDifferentInstances_ShouldBeDuplicated()
    {
        var grid = new GridIndex(cellSize: 10);

        var poly1 = _factory.CreatePolygon(new[]
        {
            new Coordinate(1, 1),
            new Coordinate(4, 1),
            new Coordinate(4, 4),
            new Coordinate(1, 4),
            new Coordinate(1, 1)
        });

        var poly2 = _factory.CreatePolygon(new[]
        {
            new Coordinate(1, 1),
            new Coordinate(4, 1),
            new Coordinate(4, 4),
            new Coordinate(0, 4),
            new Coordinate(1, 1)
        });

        grid.Insert(poly1);
        grid.Insert(poly2);

        var cell = grid.GetGridDictionary()[(0, 0)];

        // Будет 2 — потому что это разные объекты
        Assert.AreEqual(2, cell.Count);
    }
}

