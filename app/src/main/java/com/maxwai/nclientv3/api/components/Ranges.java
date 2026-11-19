package com.maxwai.nclientv3.api.components;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.StringRes;

import com.maxwai.nclientv3.R;

public class Ranges implements Parcelable {


    public static final int UNDEFINED = -1;
    public static final TimeUnit UNDEFINED_DATE = null;
    // The first field of the legacy format is a positive page count or UNDEFINED.
    private static final int PARCEL_FORMAT_WITH_FAVORITES = -2;
    public static final Creator<Ranges> CREATOR = new Creator<>() {
        @Override
        public Ranges createFromParcel(Parcel in) {
            return new Ranges(in);
        }

        @Override
        public Ranges[] newArray(int size) {
            return new Ranges[size];
        }
    };
    private int fromPage = UNDEFINED, toPage = UNDEFINED;
    private int fromDate = UNDEFINED, toDate = UNDEFINED;
    private TimeUnit fromDateUnit = UNDEFINED_DATE, toDateUnit = UNDEFINED_DATE;
    private int minFavorites = UNDEFINED;

    public Ranges() {
    }

    protected Ranges(Parcel in) {
        int date;
        int first = in.readInt();
        boolean hasFavorites = first == PARCEL_FORMAT_WITH_FAVORITES;
        fromPage = hasFavorites ? in.readInt() : first;
        toPage = in.readInt();
        fromDate = in.readInt();
        toDate = in.readInt();
        date = in.readInt();
        fromDateUnit = date == -1 ? UNDEFINED_DATE : TimeUnit.values()[date];
        date = in.readInt();
        toDateUnit = date == -1 ? UNDEFINED_DATE : TimeUnit.values()[date];
        if (hasFavorites) setMinFavorites(in.readInt());
    }

    public boolean isDefault() {
        return fromDate == UNDEFINED && toDate == UNDEFINED
            && toPage == UNDEFINED && fromPage == UNDEFINED && minFavorites == UNDEFINED;
    }

    public int getFromPage() {
        return fromPage;
    }

    public void setFromPage(int fromPage) {
        this.fromPage = fromPage;
    }

    public int getToPage() {
        return toPage;
    }

    public int getFromDate() { return fromDate; }
    public int getToDate() { return toDate; }
    public TimeUnit getFromDateUnit() { return fromDateUnit; }
    public TimeUnit getToDateUnit() { return toDateUnit; }
    public int getMinFavorites() { return minFavorites; }

    public void setMinFavorites(int minFavorites) {
        this.minFavorites = minFavorites > 0 ? minFavorites : UNDEFINED;
    }

    public void setToPage(int toPage) {
        this.toPage = toPage;
    }

    public void setFromDate(int fromDate) {
        this.fromDate = fromDate;
    }

    public void setToDate(int toDate) {
        this.toDate = toDate;
    }

    public void setFromDateUnit(TimeUnit fromDateUnit) {
        this.fromDateUnit = fromDateUnit;
    }

    public void setToDateUnit(TimeUnit toDateUnit) {
        this.toDateUnit = toDateUnit;
    }

    public String toQuery() {
        StringBuilder builder = new StringBuilder();
        if (fromPage != UNDEFINED && toPage != UNDEFINED && fromPage == toPage) {
            builder.append("pages:").append(fromPage).append(' ');
        } else {
            if (fromPage != UNDEFINED) builder.append("pages:>=").append(fromPage).append(' ');
            if (toPage != UNDEFINED) builder.append("pages:<=").append(toPage).append(' ');
        }

        if (fromDate != UNDEFINED && toDate != UNDEFINED && fromDate == toDate && fromDateUnit == toDateUnit) {
            builder.append("uploaded:").append(fromDate).append(fromDateUnit.val).append(' ');
        } else {
            if (fromDate != UNDEFINED)
                builder.append("uploaded:>=").append(fromDate).append(fromDateUnit.val).append(' ');
            if (toDate != UNDEFINED)
                builder.append("uploaded:<=").append(toDate).append(toDateUnit.val).append(' ');
        }
        if (minFavorites != UNDEFINED) builder.append("favorites:>=").append(minFavorites);
        return builder.toString().trim();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(PARCEL_FORMAT_WITH_FAVORITES);
        dest.writeInt(fromPage);
        dest.writeInt(toPage);
        dest.writeInt(fromDate);
        dest.writeInt(toDate);
        dest.writeInt(fromDateUnit == UNDEFINED_DATE ? -1 : fromDateUnit.ordinal());
        dest.writeInt(toDateUnit == UNDEFINED_DATE ? -1 : toDateUnit.ordinal());
        dest.writeInt(minFavorites);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public enum TimeUnit {
        HOUR(R.string.hours, 'h'),
        DAY(R.string.days, 'd'),
        WEEK(R.string.weeks, 'w'),
        MONTH(R.string.months, 'm'),
        YEAR(R.string.years, 'y');
        @StringRes
        final int string;
        final char val;

        TimeUnit(int string, char val) {
            this.string = string;
            this.val = val;
        }

        public int getString() {
            return string;
        }

        public char getVal() {
            return val;
        }
    }

}
