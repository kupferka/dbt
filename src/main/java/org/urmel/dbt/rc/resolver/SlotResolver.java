/*
 * $Id$ 
 * $Revision$ $Date$
 *
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * This program is free software; you can use it, redistribute it
 * and / or modify it under the terms of the GNU General Public License
 * (GPL) as published by the Free Software Foundation; either version 2
 * of the License or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program, in a file called gpl.txt or license.txt.
 * If not, write to the Free Software Foundation Inc.,
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307 USA
 */
package org.urmel.dbt.rc.resolver;

import java.util.HashMap;
import java.util.StringTokenizer;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

import org.jdom2.transform.JDOMSource;
import org.urmel.dbt.rc.datamodel.slot.Slot;
import org.urmel.dbt.rc.datamodel.slot.SlotEntry;
import org.urmel.dbt.rc.datamodel.slot.SlotEntryTypes;
import org.urmel.dbt.rc.persistency.SlotManager;
import org.urmel.dbt.rc.utils.SlotEntryTransformer;
import org.urmel.dbt.rc.utils.SlotEntryTypesTransformer;
import org.urmel.dbt.rc.utils.SlotTransformer;

/**
 * This resolver can be used to resolve a {@link Slot}, {@link SlotEntry} and also {@link SlotEntryTypes}. 
 * <br />
 * <br />
 * Syntax:
 * <ul> 
 * <li><code>slot:slotId=slotId</code> to resolve an {@link Slot}</li>
 * <li><code>slot:slotId=slotId&entryId=entryId</code> to resolve an {@link SlotEntry}</li>
 * <li><code>slot:entryTypes</code> to resolve {@link SlotEntryTypes}</li>
 * </ul>
 * 
 * @author Ren\u00E9 Adler (eagle)
 *
 */
public class SlotResolver implements URIResolver {

    private static final SlotManager SLOT_MGR = SlotManager.instance();

    /* (non-Javadoc)
     * @see javax.xml.transform.URIResolver#resolve(java.lang.String, java.lang.String)
     */
    @Override
    public Source resolve(final String href, final String base) throws TransformerException {
        try {
            final String options = href.substring(href.indexOf(":") + 1);
            final HashMap<String, String> params = new HashMap<String, String>();
            String[] param;
            final StringTokenizer tok = new StringTokenizer(options, "&");
            while (tok.hasMoreTokens()) {
                param = tok.nextToken().split("=");
                if (param.length == 1) {
                    params.put(param[0], "");
                } else {
                    params.put(param[0], param[1]);
                }
            }

            if (params.get("entryTypes") != null) {
                return new JDOMSource(SlotEntryTypesTransformer.buildExportableXML(SlotEntryTypes.instance()));
            }

            final String slotId = params.get("slotId");
            final String entryId = params.get("entryId");

            final Slot slot = SLOT_MGR.getSlotById(slotId);
            if (entryId != null) {
                final SlotEntry<?> entry = slot.getEntryById(entryId);

                return new JDOMSource(SlotEntryTransformer.buildExportableXML(entry));
            }

            return new JDOMSource(SlotTransformer.buildExportableXML(slot));
        } catch (final Exception ex) {
            throw new TransformerException("Exception resolving " + href, ex);
        }
    }

}