package Utils.DataStructures.MarkerFile;

import Utils.DataTypes.Interval;

/**
 * Created by Alex on 10.11.2017.
 */
public class Marking
{
    private int fms;
    private int lms;
    private int channel;

    public Marking( int first_sample, int last_sample, int channel )
    {
        this.channel = channel;
        fms = first_sample;
        lms = last_sample;
    }

    public void set_first_marked_sample( int fms )
    {
        this.fms = fms;
    }

    public void set_last_marked_sample( int lms )
    {
        this.lms = lms;
    }

    public Interval getInterval()
    {
        return new Interval( fms, lms + 1, false );
    }

    public int get_first_marked_sample()
    {
        return fms;
    }

    public int get_last_marked_sample()
    {
        return lms;
    }

    public int getChannel()
    {
        return channel;
    }

    @Override
    public boolean equals( Object obj )
    {
        return ( obj instanceof Marking ) &&
               ( ( ( Marking )obj ).channel == channel ) &&
               ( ( ( Marking )obj ).lms == lms ) &&
               ( ( ( Marking )obj ).fms == fms );
    }

    @Override
    public String toString()
    {
        return "[ " + fms + ", " + lms + " ], ch = " + channel;
    }

    public int get_number_of_marked_samples()
    {
        return lms - fms + 1;
    }
}
