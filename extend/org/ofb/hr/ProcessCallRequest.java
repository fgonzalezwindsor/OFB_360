/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                        *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.ofb.hr;

import java.util.logging.*;

import org.compiere.model.X_HRO_JobRequest;
import org.compiere.process.*;
import org.compiere.util.AdempiereSystemError;
import org.compiere.util.Env;
 
/**
 *	report infoprojectPROVECTIS
 *	
 *  @author Fabian Aguilar
 *  @version $ v 1.0$
 */
public class ProcessCallRequest extends SvrProcess
{
	private int record_ID;	
	
	private String p_DocAction;
	private String p_Cause;

	/**
	 *  Prepare - e.g., get Parameters.
	 */
	protected void prepare()
	{
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;
			else if(para[i].getParameterName().equals("DocAction"))
				p_DocAction = (String)para[i].getParameter();
			else if(para[i].getParameterName().equals("Cause"))
				p_Cause = (String)para[i].getParameter();
			else
				log.log(Level.SEVERE, "prepare - Unknown Parameter: " + name);
		}
		record_ID = this.getRecord_ID();
	}	//	prepare

	/**
	 *  Perrform process.
	 *  @return Message
	 *  @throws Exception if not successful
	 */
	protected String doIt() throws AdempiereSystemError
	{
		X_HRO_JobRequest jr = new X_HRO_JobRequest(Env.getCtx(), record_ID, this.get_TrxName());
		if(jr.getDocStatus().equals("DR"))
		{
			jr.setDocStatus("WC");
			jr.setProcessed(true);
			jr.save();
			return "Solicitud Enviada";
		}
		
		if(jr.getDocStatus().equals("WC"))
		{
			if(p_DocAction.equals("AP"))//aprobar
			{
				jr.setDocStatus("IP");
				jr.save();
				return "Solicitud Aprobada";
			}
			
			if(p_DocAction.equals("RJ"))//rechazar
			{
				jr.setDocStatus("RJ");
				jr.setDescription(p_Cause);
				jr.save();
				return "Solicitud Rechazada";
			}
				
			/*if(p_DocAction.equals("VO"))//anular
			{
				jr.setDocStatus("VO");
			    jr.setDescription(p_Cause);
			    jr.save();
				return "Solicitud Anulada";
			}*/
			
		}
		
				
		return "Acci�n no valida";
	}	//	doIt
	
}	//	OrderOpen
