/*
 * $Id$
 */
package org.urmel.dbt.rc.datamodel.slot;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.mycore.common.MCRException;

/**
 * @author René Adler (eagle)
 */
@XmlRootElement(name = "slots")
@XmlAccessorType(XmlAccessType.NONE)
public class SlotList implements Serializable {

    private static final long serialVersionUID = 8484254848235412462L;

    private List<Slot> slots = new ArrayList<Slot>();

    /**
     * @return the slots
     */
    public List<Slot> getSlots() {
        return slots;
    }

    /**
     * @param slots the slots to set
     */
    @XmlElement(name = "slot")
    public void setSlots(final List<Slot> slots) {
        this.slots = slots;
    }

    /**
     * @param slot the slot to add
     */
    public void addSlot(final Slot slot) {
        if (slot.getSlotId() != null && getSlotById(slot.getSlotId()) != null) {
            throw new MCRException("Slot with id " + slot.getSlotId() + " already exists!");
        }

        slots.add(slot);
    }

    /**
     * Returns a slot by given id.
     * 
     * @param slotId the slot id
     * @return the slot
     */
    public Slot getSlotById(final String slotId) {
        for (Slot slot : slots) {
            if (slotId.equals(slot.getSlotId())) {
                return slot;
            }
        }

        return null;
    }

    /**
     * Returns a {@link SlotList} with only active {@link Slot}s.
     * 
     * @return the {@link SlotList}
     */
    public SlotList getActiveSlots() {
        final SlotList slotList = new SlotList();

        for (Slot slot : slots) {
            if (slot.isActive()) {
                slotList.addSlot(slot.getBasicCopy());
            }
        }

        return slotList;
    }
}