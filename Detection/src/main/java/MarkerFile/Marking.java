package MarkerFile;

import Utils.Interval;

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
}
