package Utils.DataStructures;

import Utils.DataTypes.Interval;
import org.junit.Assert;

/**
 * Created by Alex on 01.06.2018.
 */
public class OrderedNonOverlappingIntervalSetTest
{
    /**
     * Test case #1: Deleting
     * Add intervals: [0,1000)
     * Delete intervals: [100,200), [800,900), [500, 550)
     * Expected set: [0,100), [200, 500), [550,800), [900,1000)
     */
    @org.junit.Test
    public void remove_TC01() throws Exception
    {
        OrderedNonOverlappingIntervalSet set = new OrderedNonOverlappingIntervalSet();

        set.add( new Interval( 0, 1000, false ) );
        set.remove( new Interval( 100, 200, false ) );
        set.remove( new Interval( 800, 900, false ) );
        set.remove( new Interval( 500, 550, false ) );

        Assert.assertEquals( set.getSize(), 4 );

        set.moveToPosition( 0 );
        Assert.assertEquals( set.getCurrent(), new Interval( 0, 100, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 200, 500, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 550, 800, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 900, 1000, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), null );
    }

    /**
     * Test case #2: Deleting
     * Add intervals: [0,1000)
     * Delete intervals: [400,600), [450,550)
     * Expected set: [0,400), [600, 1000)
     */
    @org.junit.Test
    public void remove_TC02() throws Exception
    {
        OrderedNonOverlappingIntervalSet set = new OrderedNonOverlappingIntervalSet();

        set.add( new Interval( 0, 1000, false ) );
        set.remove( new Interval( 400, 600, false ) );
        set.remove( new Interval( 450, 550, false ) );

        Assert.assertEquals( set.getSize(), 2 );

        set.moveToPosition( 0 );
        Assert.assertEquals( set.getCurrent(), new Interval( 0, 400, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 600, 1000, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), null );
    }

    /**
     * Test case #3: Deleting
     * Add intervals: [0,1000)
     * Delete intervals: [450,550), [400,600)
     * Expected set: [0,400), [600, 1000)
     */
    @org.junit.Test
    public void remove_TC03() throws Exception
    {
        OrderedNonOverlappingIntervalSet set = new OrderedNonOverlappingIntervalSet();

        set.add( new Interval( 0, 1000, false ) );
        set.remove( new Interval( 450, 550, false ) );
        set.remove( new Interval( 400, 600, false ) );

        Assert.assertEquals( set.getSize(), 2 );

        set.moveToPosition( 0 );
        Assert.assertEquals( set.getCurrent(), new Interval( 0, 400, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 600, 1000, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), null );
    }

    /**
     * Test case #4: Deleting
     * Add intervals: [0,1000)
     * Delete intervals: [450,550), [400,450), [550, 600), [700,750), [650,750), [700, 800)
     * Expected set: [0,400), [600, 650), [800,1000)
     */
    @org.junit.Test
    public void remove_TC04() throws Exception
    {
        OrderedNonOverlappingIntervalSet set = new OrderedNonOverlappingIntervalSet();

        set.add( new Interval( 0, 1000, false ) );
        set.remove( new Interval( 450, 550, false ) );
        set.remove( new Interval( 400, 450, false ) );
        set.remove( new Interval( 550, 600, false ) );
        set.remove( new Interval( 700, 750, false ) );
        set.remove( new Interval( 650, 750, false ) );
        set.remove( new Interval( 700, 800, false ) );

        Assert.assertEquals( set.getSize(), 3 );

        set.moveToPosition( 0 );
        Assert.assertEquals( set.getCurrent(), new Interval( 0, 400, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 600, 650, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 800, 1000, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), null );
    }

    /**
     * Test case #5: Deleting
     * Add intervals: [0,1000)
     * Delete intervals: [500,550), [450,500),[450,550),[550,600),[500,600),[750,800),[850,900),[600,850]
     * Expected set: [0,450), [900, 1000)
     */
    @org.junit.Test
    public void remove_TC05() throws Exception
    {
        OrderedNonOverlappingIntervalSet set = new OrderedNonOverlappingIntervalSet();

        set.add( new Interval( 0, 1000, false ) );
        Assert.assertEquals( set.getSize(), 1 );
        set.remove( new Interval( 500, 550, false ) );
        Assert.assertEquals( set.getSize(), 2 );
        set.remove( new Interval( 450, 500, false ) );
        Assert.assertEquals( set.getSize(), 2 );
        set.remove( new Interval( 450, 550, false ) );
        Assert.assertEquals( set.getSize(), 2 );
        set.remove( new Interval( 550, 600, false ) );
        Assert.assertEquals( set.getSize(), 2 );
        set.remove( new Interval( 500, 600, false ) );
        Assert.assertEquals( set.getSize(), 2 );
        set.remove( new Interval( 750, 800, false ) );
        Assert.assertEquals( set.getSize(), 3 );
        set.remove( new Interval( 850, 900, false ) );
        Assert.assertEquals( set.getSize(), 4 );
        set.remove( new Interval( 600, 850, false ) );
        Assert.assertEquals( set.getSize(), 2 );

        set.moveToPosition( 0 );
        Assert.assertEquals( set.getCurrent(), new Interval( 0, 450, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 900, 1000, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), null );
    }

    @org.junit.Test
    /**
     * Test case #1: No overlapping on adding; testing the intervals are sorted
     * Add intervals: [0,100), [200, 250), [500,520), [300,350)
     *
     * Expected set: [0,100), [200, 250), [300,350), [500,520)
     */
    public void add_TC01() throws Exception
    {
        Interval i1 = new Interval( 0, 100, false );
        Interval i2 = new Interval( 200, 250, false );
        Interval i3 = new Interval( 500, 520, false );
        Interval i4 = new Interval( 300, 350, false );

        OrderedNonOverlappingIntervalSet set = new OrderedNonOverlappingIntervalSet();

        set.add( i1 );
        set.add( i2 );
        set.add( i3 );
        set.add( i4 );

        Assert.assertEquals( set.getSize(), 4 );

        set.moveCursorOnFirst();
        Assert.assertEquals( set.getCurrent(), i1 );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), i2 );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), i4 );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), i3 );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), null );
    }

    @org.junit.Test
    /**
     * Test case #2: No overlapping on adding; testing the intervals are sorted
     * Add intervals: [500,500],[400,400],[600,600],[300,300],[700,700],[200,200]
     *
     * Expected set: [200,200],[300,300],[400,400],[500,500],[600,600],[700,700]
     */
    public void add_TC02() throws Exception
    {
        Interval i1 = new Interval( 500, 1 );
        Interval i2 = new Interval( 400, 1 );
        Interval i3 = new Interval( 600, 1 );
        Interval i4 = new Interval( 300, 1 );
        Interval i5 = new Interval( 700, 1 );
        Interval i6 = new Interval( 200, 1 );

        OrderedNonOverlappingIntervalSet set = new OrderedNonOverlappingIntervalSet();

        set.add( i1 );
        set.add( i2 );
        set.add( i3 );
        set.add( i4 );
        set.add( i5 );
        set.add( i6 );

        Assert.assertEquals( set.getSize(), 6 );

        set.moveToPosition( 0 );
        Assert.assertEquals( set.getCurrent(), i6 );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), i4 );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), i2 );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), i1 );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), i3 );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), i5 );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), null );
    }

    @org.junit.Test
    /**
     * Test case #3: Overlapping on adding; testing the intervals are sorted and intervals are compacted
     * Add intervals: [0,100), [200,300), [300,350), [150,200)
     *
     * Expected set: [0,100), [150,350)
     */
    public void add_TC03() throws Exception
    {
        Interval i1 = new Interval( 0, 100 );
        Interval i2 = new Interval( 200, 100 );
        Interval i3 = new Interval( 300, 50 );
        Interval i4 = new Interval( 150, 50 );

        OrderedNonOverlappingIntervalSet set = new OrderedNonOverlappingIntervalSet();

        set.add( i1 );
        set.add( i2 );
        set.add( i3 );
        set.add( i4 );

        Assert.assertEquals( set.getSize(), 2 );

        set.moveToPosition( 0 );
        Assert.assertEquals( set.getCurrent(), i1 );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 150, 350, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), null );
    }

    @org.junit.Test
    /**
     * Test case #4: Overlapping on adding; testing the intervals are sorted and intervals are compacted
     * Add intervals: [200,250), [225,240), [225, 275), [175,199)
     *
     * Expected set: [175,199), [200,275)
     */
    public void add_TC04() throws Exception
    {
        Interval i1 = new Interval( 200, 50 );
        Interval i2 = new Interval( 225, 15 );
        Interval i3 = new Interval( 225, 50 );
        Interval i4 = new Interval( 175, 24 );

        OrderedNonOverlappingIntervalSet set = new OrderedNonOverlappingIntervalSet();

        set.add( i1 );
        set.add( i2 );
        set.add( i3 );
        set.add( i4 );

        Assert.assertEquals( set.getSize(), 2 );

        set.moveToPosition( 0 );
        Assert.assertEquals( set.getCurrent(), i4 );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 200, 275, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), null );
    }

    @org.junit.Test
    /**
     * Test case #5: Step by step: Overlapping on adding; testing the intervals are sorted and intervals are compacted
     * Add intervals: [500,570),[100,150),[300,375),[400,425), [125,175),[375,400),[470,505),[470,570),[470,600), [0,700)
     *
     * Expected set: [0,700)
     */
    public void add_TC05() throws Exception
    {
        OrderedNonOverlappingIntervalSet set = new OrderedNonOverlappingIntervalSet();

        set.add( new Interval( 500, 570, false ) );
        set.add( new Interval( 100, 150, false ) );
        set.add( new Interval( 300, 375, false ) );
        set.add( new Interval( 400, 425, false ) );

        Assert.assertEquals( set.getSize(), 4 );

        set.moveToPosition( 0 );
        Assert.assertEquals( set.getCurrent(), new Interval( 100, 150, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 300, 375, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 400, 425, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 500, 570, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), null );

        set.add( new Interval( 125, 175, false ) );
        set.add( new Interval( 375, 400, false ) );
        set.add( new Interval( 470, 505, false ) );
        set.add( new Interval( 470, 570, false ) );

        Assert.assertEquals( set.getSize(), 3 );

        set.moveToPosition( 0 );
        Assert.assertEquals( set.getCurrent(), new Interval( 100, 175, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 300, 425, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), new Interval( 470, 570, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), null );

        set.add( new Interval( 470, 600, false ) );
        set.add( new Interval( 0, 700, false ) );

        Assert.assertEquals( set.getSize(), 1 );

        Assert.assertEquals( set.getPosition(), 0 );
        Assert.assertEquals( set.getCurrent(), new Interval( 0, 700, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), null );

    }

    @org.junit.Test
    /**
     * Test case #6: Overlapping on adding; testing the intervals are sorted and intervals are compacted
     * Add intervals: [0,100],[200,300],[100,200]
     *
     * Expected set: [0,700)
     */
    public void add_TC06() throws Exception
    {
        OrderedNonOverlappingIntervalSet set = new OrderedNonOverlappingIntervalSet();

        set.add( new Interval( 0,101,false ) );
        Assert.assertEquals( set.getSize(), 1 );
        set.add( new Interval( 201,301,false ) );
        Assert.assertEquals( set.getSize(), 2 );
        set.add( new Interval( 100,201,false ) );
        Assert.assertEquals( set.getSize(), 1 );

        set.moveToPosition( 0 );
        Assert.assertEquals( set.getCurrent(), new Interval( 0, 301, false ) );
        set.moveCursorNext();
        Assert.assertEquals( set.getCurrent(), null );
    }
}